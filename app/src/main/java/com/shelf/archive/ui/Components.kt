package com.shelf.archive.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.archive.domain.FileKind
import com.shelf.archive.domain.FileKinds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun kindColor(kind: FileKind): Color = when (kind) {
    FileKind.IMAGE -> Color(0xFF1E4E7B)
    FileKind.WHATSAPP -> Color(0xFF0E6B52)
    FileKind.PDF -> Color(0xFF9F2D2D)
    FileKind.OFFICE -> Color(0xFF8A4B32)
    FileKind.TEXT -> Color(0xFF3E5C4A)
    FileKind.OTHER -> Color(0xFF4A453C)
}

fun relativeTime(millis: Long): String {
    if (millis <= 0L) return "Just now"
    return DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

fun absoluteTime(millis: Long): String {
    if (millis <= 0L) return "Waiting for the server time"
    return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(millis))
}

@Composable
fun ShelfHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun KindGlyph(kind: FileKind, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(kindColor(kind), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = FileKinds.extensionLabel(name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun LinkLine(url: String, modifier: Modifier = Modifier) {
    Text(
        text = url,
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiary,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun NoticeCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun MetaRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
