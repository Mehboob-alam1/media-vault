package com.shelf.archive.data

import android.content.Context
import com.shelf.archive.domain.FirebaseConfig

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): FirebaseConfig? {
        val projectId = prefs.getString(KEY_PROJECT, null) ?: return null
        val config = FirebaseConfig(
            projectId = projectId,
            applicationId = prefs.getString(KEY_APP, "").orEmpty(),
            apiKey = prefs.getString(KEY_API, "").orEmpty(),
            storageBucket = prefs.getString(KEY_BUCKET, "").orEmpty(),
            databaseUrl = prefs.getString(KEY_DB, "").orEmpty(),
        )
        return config.takeIf { it.validationError() == null }
    }

    fun write(config: FirebaseConfig) {
        val normalized = config.normalized()
        prefs.edit()
            .putString(KEY_PROJECT, normalized.projectId)
            .putString(KEY_APP, normalized.applicationId)
            .putString(KEY_API, normalized.apiKey)
            .putString(KEY_BUCKET, normalized.storageBucket)
            .putString(KEY_DB, normalized.databaseUrl)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_PROJECT)
            .remove(KEY_APP)
            .remove(KEY_API)
            .remove(KEY_BUCKET)
            .remove(KEY_DB)
            .apply()
    }

    private companion object {
        const val PREFS = "shelf"
        const val KEY_PROJECT = "project_id"
        const val KEY_APP = "application_id"
        const val KEY_API = "api_key"
        const val KEY_BUCKET = "storage_bucket"
        const val KEY_DB = "database_url"
    }
}
