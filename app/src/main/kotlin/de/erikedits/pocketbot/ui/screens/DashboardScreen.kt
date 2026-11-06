package de.erikedits.pocketbot.ui.screens

import android.content.Intent
import android.net.TrafficStats
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.engine.BotStatus
import de.erikedits.pocketbot.engine.EngineState
import de.erikedits.pocketbot.engine.LogLevel
import de.erikedits.pocketbot.engine.isActive
import de.erikedits.pocketbot.service.BotRuntimeService
import de.erikedits.pocketbot.shared.discord.InviteLink
import de.erikedits.pocketbot.shared.util.HosterAdvice
import de.erikedits.pocketbot.ui.theme.PbColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.DateFormat
import java.util.Date

/** Per-bot dashboard (SPEC 4.5): status, uptime, ping, logs, data usage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(container: AppContainer, botId: String, onBack: () -> Unit, onOpenRules: () -> Unit) {
    val context = LocalContext.current
    val bots by container.bots.bots.collectAsStateWithLifecycle()
    val bot = bots.firstOrNull { it.id == botId }
    val states by container.botManager.states.collectAsStateWithLifecycle()
    val problems by container.botManager.problems.collectAsStateWithLifecycle()
    val configs by container.configs.configs.collectAsStateWithLifecycle()
    val initialConfig = remember(botId) { container.configs.get(botId) }
    val functionCount = (configs[botId] ?: initialConfig).rules.count { it.enabled }
    val state = states[botId] ?: EngineState()
    val engine = container.botManager.engine(botId)
    val emptyLogs = remember { MutableStateFlow(container.botManager.savedLog(botId)) }
    val logs by (engine?.logs ?: emptyLogs).collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    if (bot == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bot.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.dashboard_delete)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(bot.name, bot.avatarUrl, 56.dp)
                        Column(Modifier.weight(1f)) {
                            Text(bot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            StatusLine(state.status)
                        }
                    }
                    val running = state.status.isActive || state.status == BotStatus.PAUSED
                    if (running) {
                        Button(
                            onClick = { container.botManager.stop(botId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PbColors.Red),
                        ) { Text(stringResource(R.string.dashboard_stop)) }
                    } else {
                        Button(
                            onClick = { BotRuntimeService.startBot(context, botId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PbColors.Green),
                        ) { Text(stringResource(R.string.dashboard_start)) }
                    }
                    val problem = problems[botId]
                    if (problem != null) {
                        Text(
                            stringResource(if (problem == "no_token") R.string.dashboard_no_token else R.string.dashboard_token_unreadable),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (problem != "no_token") {
                            Text(problem, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (problem != null || state.status == BotStatus.INVALID_TOKEN) {
                        OutlinedButton(onClick = { showTokenDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.token_dialog_title))
                        }
                    }
                    when (state.status) {
                        BotStatus.INVALID_TOKEN -> Text(stringResource(R.string.dashboard_invalid_token_help), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        BotStatus.DISALLOWED_INTENTS -> Text(stringResource(R.string.dashboard_intents_help), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        BotStatus.PAUSED -> Text(stringResource(R.string.dashboard_paused_help, container.prefs.batteryResume), color = PbColors.Yellow, style = MaterialTheme.typography.bodySmall)
                        else -> Unit
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.dashboard_functions)) {
                    Text(stringResource(R.string.dashboard_functions_text, functionCount), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenRules, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dashboard_functions_edit)) }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.dashboard_stats)) {
                    StatRow(stringResource(R.string.dashboard_uptime), state.onlineSince?.let { formatDuration(now - it) } ?: "–")
                    StatRow(stringResource(R.string.dashboard_ping), if (state.pingMs >= 0 && state.status == BotStatus.ONLINE) "${state.pingMs} ms" else "–")
                    StatRow(stringResource(R.string.dashboard_servers), state.guilds.toString())
                    StatRow(stringResource(R.string.dashboard_members), state.members.toString())
                    val uid = Process.myUid()
                    val traffic = TrafficStats.getUidRxBytes(uid).let { rx -> if (rx < 0) -1 else rx + TrafficStats.getUidTxBytes(uid) }
                    StatRow(stringResource(R.string.dashboard_data), formatBytes(traffic))
                    Text(stringResource(R.string.dashboard_data_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (HosterAdvice.shouldRecommend(bot.multiServer, state.guilds, state.members)) {
                item {
                    SectionCard(title = stringResource(R.string.hoster_title)) {
                        Text(stringResource(R.string.dashboard_hoster_hint, state.guilds, state.members), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { CloudCard(container, bot) }
            item {
                SectionCard(title = stringResource(R.string.dashboard_invite)) {
                    val link = InviteLink.build(bot.id)
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(send, null))
                    }) { Text(stringResource(R.string.share)) }
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }) { Text(stringResource(R.string.open)) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dashboard_logs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = {
                            val text = buildString {
                                append("PocketBot ${de.erikedits.pocketbot.BuildConfig.VERSION_NAME} (${de.erikedits.pocketbot.BuildConfig.VERSION_CODE}) · ")
                                append("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                                append("Status: ${state.status} ${state.error ?: ""}\n\n")
                                val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
                                logs.takeLast(300).forEach { append(fmt.format(Date(it.time))).append(' ').append(it.level).append(' ').append(it.message).append('\n') }
                            }
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, null))
                        }) { Text(stringResource(R.string.dashboard_share_log)) }
                    }
                }
                if (logs.isEmpty()) Text(stringResource(R.string.dashboard_logs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(logs.takeLast(300).asReversed()) { entry ->
                val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(entry.time))
                Text(
                    "$time  ${entry.message}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (entry.level) {
                        LogLevel.ERROR -> PbColors.Red
                        LogLevel.WARN -> PbColors.Yellow
                        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }

    if (showTokenDialog) TokenDialog(container, bot) { showTokenDialog = false }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dashboard_delete_title, bot.name)) },
            text = { Text(stringResource(R.string.dashboard_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    container.botManager.deleteData(botId)
                    container.bots.delete(botId)
                }) { Text(stringResource(R.string.dashboard_delete), color = PbColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
