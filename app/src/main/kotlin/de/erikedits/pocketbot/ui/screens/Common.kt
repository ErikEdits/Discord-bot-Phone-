package de.erikedits.pocketbot.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.engine.BotStatus
import de.erikedits.pocketbot.ui.theme.PbColors

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun StatusDot(status: BotStatus, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(statusColor(status)))
}

@Composable
fun StatusLine(status: BotStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusDot(status)
        Text(androidx.compose.ui.res.stringResource(statusLabel(status)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun statusColor(status: BotStatus): Color = when (status) {
    BotStatus.ONLINE -> PbColors.Green
    BotStatus.CONNECTING, BotStatus.RECONNECTING, BotStatus.PAUSED -> PbColors.Yellow
    BotStatus.INVALID_TOKEN, BotStatus.DISALLOWED_INTENTS, BotStatus.ERROR -> PbColors.Red
    BotStatus.STOPPED -> PbColors.Grey
}

@StringRes
fun statusLabel(status: BotStatus): Int = when (status) {
    BotStatus.STOPPED -> R.string.status_stopped
    BotStatus.CONNECTING -> R.string.status_connecting
    BotStatus.ONLINE -> R.string.status_online
    BotStatus.RECONNECTING -> R.string.status_reconnecting
    BotStatus.PAUSED -> R.string.status_paused
    BotStatus.INVALID_TOKEN -> R.string.status_invalid_token
    BotStatus.DISALLOWED_INTENTS -> R.string.status_disallowed_intents
    BotStatus.ERROR -> R.string.status_error
}

/** Round avatar with the first letter (no image loader in the skeleton yet). */
@Composable
fun InitialAvatar(name: String, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(PbColors.Blurple),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

fun formatDuration(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    val d = s / 86_400
    val h = (s % 86_400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        d > 0 -> "${d}d ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${sec}s"
        else -> "${sec}s"
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "–"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
