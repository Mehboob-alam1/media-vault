package com.shelf.archive.ui.preview

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shelf.archive.data.RemoteFiles
import com.shelf.archive.domain.FileKind
import com.shelf.archive.domain.StoredFile
import com.shelf.archive.ui.KindGlyph
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FilePreview(file: StoredFile, modifier: Modifier = Modifier) {
    when (file.category) {
        FileKind.IMAGE, FileKind.WHATSAPP -> ImagePreview(file, modifier)
        FileKind.PDF -> PdfPreview(file, modifier)
        FileKind.TEXT -> TextPreview(file, modifier)
        else -> OfficeNote(file, modifier)
    }
}

@Composable
private fun ImagePreview(file: StoredFile, modifier: Modifier) {
    var failed by remember(file.downloadUrl) { mutableStateOf(false) }
    if (failed) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            KindGlyph(file.category, file.name, Modifier.padding(24.dp))
        }
        return
    }
    AsyncImage(
        model = file.downloadUrl,
        contentDescription = file.name,
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
        contentScale = ContentScale.Fit,
        onError = { failed = true },
    )
}

@Composable
private fun PdfPreview(file: StoredFile, modifier: Modifier) {
    val context = LocalContext.current
    val pages = remember(file.id, file.downloadUrl) { mutableStateListOf<Bitmap>() }
    var hidden by remember(file.id, file.downloadUrl) { mutableStateOf(0) }
    var error by remember(file.id, file.downloadUrl) { mutableStateOf<String?>(null) }
    var loading by remember(file.id, file.downloadUrl) { mutableStateOf(true) }
    LaunchedEffect(file.id, file.downloadUrl) {
        loading = true
        error = null
        pages.clear()
        try {
            val rendered = withContext(Dispatchers.IO) {
                val destination = RemoteFiles.destination(context, file.id, file.downloadUrl, file.name)
                RemoteFiles.download(file.downloadUrl, destination, maxBytes = 80L * 1024 * 1024)
                renderPdf(destination)
            }
            pages.addAll(rendered.first)
            hidden = rendered.second
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            error = failure.message ?: "Couldn't preview this PDF."
        } finally {
            loading = false
        }
    }
    DisposableEffect(file.id, file.downloadUrl) {
        onDispose {
            pages.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            pages.clear()
        }
    }
    PreviewFrame(loading = loading, error = error, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            pages.forEach { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF page",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth,
                )
            }
            if (hidden > 0) {
                Text(
                    text = "$hidden more page${if (hidden == 1) "" else "s"}. Open the file in another app to read the rest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TextPreview(file: StoredFile, modifier: Modifier) {
    val context = LocalContext.current
    var body by remember(file.id, file.downloadUrl) { mutableStateOf<String?>(null) }
    var error by remember(file.id, file.downloadUrl) { mutableStateOf<String?>(null) }
    var loading by remember(file.id, file.downloadUrl) { mutableStateOf(true) }
    LaunchedEffect(file.id, file.downloadUrl) {
        loading = true
        error = null
        if (file.sizeBytes > 2L * 1024 * 1024) {
            loading = false
            error = "This text file is over 2 MB. Open the download link to read all of it."
            return@LaunchedEffect
        }
        try {
            body = withContext(Dispatchers.IO) {
                val destination = RemoteFiles.destination(context, file.id, file.downloadUrl, file.name)
                RemoteFiles.download(file.downloadUrl, destination, maxBytes = 2L * 1024 * 1024)
                val text = destination.readText()
                if (text.length > 200_000) {
                    text.take(200_000) + "\n\n— Preview truncated —"
                } else {
                    text
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            error = failure.message ?: "Couldn't read this text file."
        } finally {
            loading = false
        }
    }
    PreviewFrame(loading = loading, error = error, modifier = modifier) {
        Text(
            text = body.orEmpty().ifBlank { "This file is empty." },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(12.dp),
        )
    }
}

@Composable
private fun OfficeNote(file: StoredFile, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KindGlyph(file.category, file.name)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Office files open in Word, Excel, PowerPoint, or another app installed on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewFrame(
    loading: Boolean,
    error: String?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        when {
            loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> content()
        }
    }
}

private fun renderPdf(file: File, maxPages: Int = 8): Pair<List<Bitmap>, Int> {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val shown = min(renderer.pageCount, maxPages)
            val bitmaps = ArrayList<Bitmap>(shown)
            for (index in 0 until shown) {
                renderer.openPage(index).use { page ->
                    val width = 1080
                    val height = (page.height * (width.toFloat() / page.width.toFloat()))
                        .toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    val matrix = Matrix().apply {
                        setScale(width.toFloat() / page.width, height.toFloat() / page.height)
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps += bitmap
                }
            }
            return bitmaps to (renderer.pageCount - shown).coerceAtLeast(0)
        }
    }
}
