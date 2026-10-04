package com.shelf.archive.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shelf.archive.data.BundledFirebase
import com.shelf.archive.data.DeviceScanner
import com.shelf.archive.data.FirebaseVault
import com.shelf.archive.data.UploadLedger
import com.shelf.archive.data.explainFirebase
import com.shelf.archive.domain.isUploadDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncService : Service() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> },
    )
    private var running = false
    private var keepNotice = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground("Saving files")
        if (!running) {
            running = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { sync() }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    keepNotice = true
                    withContext(Dispatchers.Main) { showFinished(error.explainFirebase()) }
                } finally {
                    running = false
                    if (keepNotice) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun sync() {
        val config = try {
            BundledFirebase.read(this)
        } catch (_: Exception) {
            withContext(Dispatchers.Main) { startInForeground("Couldn't read Firebase setup") }
            return
        }
        val vault = FirebaseVault(this)
        try {
            vault.connect(config)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            keepNotice = true
            withContext(Dispatchers.Main) { showFinished(error.explainFirebase()) }
            return
        }
        val ledger = UploadLedger(this)
        val discovered = DeviceScanner.scan(this)
        var done = 0
        for (item in discovered) {
            if (ledger.contains(item.fingerprint)) {
                done++
                continue
            }
            try {
                vault.upload(item.staged) { }
                ledger.mark(item.fingerprint)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep going so one bad file does not stop the rest.
            }
            done++
            val kind = if (isUploadDocument(item.staged.category)) "documents" else "photos"
            val note = "Saving $kind ($done of ${discovered.size})"
            withContext(Dispatchers.Main) { startInForeground(note) }
        }
    }

    private fun startInForeground(text: String) {
        val notification = notification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Shelf")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun showFinished(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Sync problems",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Shelf")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "shelf.sync"
        const val ALERT_CHANNEL_ID = "shelf.sync.alert"
        const val NOTIFICATION_ID = 41
    }
}
