package de.erikedits.pocketbot.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import de.erikedits.pocketbot.BuildConfig
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.data.AuthState
import de.erikedits.pocketbot.ui.theme.PbColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val prefs = container.prefs
    var protection by remember { mutableStateOf(prefs.batteryProtection) }
    var low by remember { mutableFloatStateOf(prefs.batteryLow.toFloat()) }
    var resume by remember { mutableFloatStateOf(prefs.batteryResume.toFloat()) }
    var unrestricted by remember { mutableStateOf(BatteryHelp.isUnrestricted(context)) }
    var showRestore by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(container.crashLog.read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = BatteryHelp.isUnrestricted(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_account)) {
                val user = (auth as? AuthState.LoggedIn)?.user
                if (user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(user.displayName, user.avatarUrl, 56.dp)
                        Column {
                            Text(user.displayName, fontWeight = FontWeight.SemiBold)
                            Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedButton(onClick = { scope.launch { container.auth.logout() } }) { Text(stringResource(R.string.settings_logout)) }
            }

            SectionCard(title = stringResource(R.string.vault_card_title)) {
                Text(stringResource(R.string.vault_settings_text), style = MaterialTheme.typography.bodySmall)
                if (container.cloudAvailable()) {
                    TextButton(onClick = { showRestore = true }) { Text(stringResource(R.string.vault_restore_title)) }
                } else {
                    Text(stringResource(R.string.vault_needs_login), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            crash?.let { report ->
                SectionCard(title = stringResource(R.string.crash_title)) {
                    Text(report.lines().take(8).joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = PbColors.Red)
                    Row {
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                            context.startActivity(Intent.createChooser(send, null))
                        }) { Text(stringResource(R.string.share)) }
                        TextButton(onClick = { container.crashLog.clear(); crash = null }) { Text(stringResource(R.string.dashboard_delete)) }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.settings_battery)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_battery_protection), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_battery_protection_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = protection, onCheckedChange = { protection = it; prefs.batteryProtection = it })
                }
                if (protection) {
                    Text(stringResource(R.string.settings_battery_low, low.roundToInt()))
                    Slider(
                        value = low, valueRange = 5f..50f, steps = 8,
                        onValueChange = { low = it },
                        onValueChangeFinished = {
                            prefs.batteryLow = low.roundToInt()
                            if (resume < low + 5) { resume = low + 5; prefs.batteryResume = resume.roundToInt() }
                        },
                    )
                    Text(stringResource(R.string.settings_battery_resume, resume.roundToInt()))
                    Slider(
                        value = resume, valueRange = 10f..90f, steps = 15,
                        onValueChange = { resume = it.coerceAtLeast(low + 5) },
                        onValueChangeFinished = { prefs.batteryResume = resume.roundToInt() },
                    )
                }
                Text(
                    stringResource(if (unrestricted) R.string.settings_battery_unrestricted else R.string.settings_battery_restricted),
                    color = if (unrestricted) PbColors.Green else PbColors.Yellow,
                )
                if (!unrestricted) {
                    TextButton(onClick = { BatteryHelp.requestUnrestricted(context) }) { Text(stringResource(R.string.battery_banner_button)) }
                }
                Text(stringResource(R.string.settings_battery_maker_title, Build.MANUFACTURER), fontWeight = FontWeight.SemiBold)
                Text(stringResource(BatteryHelp.manufacturerTip()), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { BatteryHelp.openAppDetails(context) }) { Text(stringResource(R.string.settings_open_app_details)) }
            }

            SectionCard(title = stringResource(R.string.settings_language)) {
                Text(stringResource(R.string.settings_language_text), style = MaterialTheme.typography.bodySmall)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                    }) { Text(stringResource(R.string.settings_language_open)) }
                }
            }

            SectionCard(title = stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                Text(stringResource(R.string.settings_server, BuildConfig.POCKETBOT_API_URL), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}")))
                }) { Text(stringResource(R.string.settings_github)) }
            }
            Column(Modifier.fillMaxWidth()) {}
        }
    }
    if (showRestore) RestoreDialog(container) { showRestore = false }
}
