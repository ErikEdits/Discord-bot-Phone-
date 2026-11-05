package de.erikedits.pocketbot.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.engine.BotStatus
import de.erikedits.pocketbot.engine.isActive
import de.erikedits.pocketbot.ui.theme.PbColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotListScreen(
    container: AppContainer,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val bots by container.bots.bots.collectAsStateWithLifecycle()
    val states by container.botManager.states.collectAsStateWithLifecycle()
    var unrestricted by remember { mutableStateOf(BatteryHelp.isUnrestricted(context)) }
    var showRestore by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(container.crashLog.read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = BatteryHelp.isUnrestricted(context) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        container.auth.refresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !container.prefs.notificationsAsked) {
            container.prefs.notificationsAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bots_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.bots_create)) },
                containerColor = PbColors.Blurple,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            crash?.let { report ->
                item {
                    SectionCard(title = stringResource(R.string.crash_banner_title)) {
                        Text(stringResource(R.string.crash_banner_text), style = MaterialTheme.typography.bodySmall)
                        Text(report.lines().filter { it.isNotBlank() }.take(6).joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = PbColors.Red)
                        Row {
                            TextButton(onClick = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, report)
                                context.startActivity(android.content.Intent.createChooser(send, null))
                            }) { Text(stringResource(R.string.share)) }
                            TextButton(onClick = { container.crashLog.clear(); crash = null }) { Text(stringResource(R.string.dashboard_delete)) }
                        }
                    }
                }
            }
            val anyActive = states.values.any { it.status.isActive }
            if (!unrestricted && (anyActive || bots.isNotEmpty())) {
                item {
                    SectionCard(title = stringResource(R.string.battery_banner_title)) {
                        Text(stringResource(R.string.battery_banner_text), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { BatteryHelp.requestUnrestricted(context) }) { Text(stringResource(R.string.battery_banner_button)) }
                    }
                }
            }
            if (bots.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🤖", style = MaterialTheme.typography.displayMedium)
                        Text(stringResource(R.string.bots_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.bots_empty_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (container.cloudAvailable()) {
                            TextButton(onClick = { showRestore = true }) { Text(stringResource(R.string.vault_restore_title)) }
                        }
                    }
                }
            }
            items(bots, key = { it.id }) { bot ->
                val state = states[bot.id]
                Card(
                    Modifier.fillMaxWidth().clickable { onOpen(bot.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(bot.name, bot.avatarUrl)
                        Column(Modifier.weight(1f)) {
                            Text(bot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            StatusLine(state?.status ?: BotStatus.STOPPED)
                        }
                        if (state != null && state.status == BotStatus.ONLINE) {
                            Text(
                                stringResource(R.string.bots_servers_short, state.guilds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showRestore) RestoreDialog(container) { showRestore = false }
}
