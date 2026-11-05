package de.erikedits.pocketbot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.data.AuthState
import de.erikedits.pocketbot.data.StoredBot
import de.erikedits.pocketbot.shared.api.ApiException
import de.erikedits.pocketbot.shared.vault.VaultCrypto
import de.erikedits.pocketbot.shared.vault.WrongPinException
import de.erikedits.pocketbot.ui.theme.PbColors
import kotlinx.coroutines.launch

/** Cloud needs a real backend login (not the debug skip). */
fun AppContainer.cloudAvailable(): Boolean = (auth.state.value as? AuthState.LoggedIn)?.devMode == false

/** Two PIN fields + warning. [pin] is only reported once both match and are long enough. */
@Composable
fun PinSetupFields(onValidPin: (String?) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    fun report(p: String, r: String) = onValidPin(if (VaultCrypto.isValidPin(p) && p == r) p else null)
    Text(stringResource(R.string.vault_pin_warning), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow)
    OutlinedTextField(
        value = pin,
        onValueChange = { pin = it; report(it, repeat) },
        label = { Text(stringResource(R.string.vault_pin_label, VaultCrypto.MIN_PIN_LENGTH)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = repeat,
        onValueChange = { repeat = it; report(pin, it) },
        label = { Text(stringResource(R.string.vault_pin_repeat)) },
        singleLine = true,
        isError = repeat.isNotEmpty() && repeat != pin,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Dialog to set the vault PIN, then run [onPin]. */
@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var valid by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_pin_setup_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PinSetupFields { valid = it } } },
        confirmButton = { TextButton(enabled = valid != null, onClick = { valid?.let(onPin) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Restore all bots from the cloud vault with the PIN. */
@Composable
fun RestoreDialog(container: AppContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<Int?>(null) }
    var done by remember { mutableStateOf(false) }
    val errorWrongPin = stringResource(R.string.vault_error_wrong_pin)
    val errorNetwork = stringResource(R.string.vault_error_network)
    val restoredText = stringResource(R.string.vault_restored)
    LaunchedEffect(Unit) {
        available = runCatching { container.vault.count() }.getOrElse { message = errorNetwork; null }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.vault_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val n = available) {
                    null -> if (message == null) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator() }
                    0 -> Text(stringResource(R.string.vault_restore_empty))
                    else -> {
                        Text(stringResource(R.string.vault_restore_found, n))
                        if (!done) OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it; message = null },
                            label = { Text(stringResource(R.string.vault_pin_enter)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (busy) CircularProgressIndicator()
                message?.let { Text(it, color = if (done) PbColors.Green else MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (done || available == 0 || (available == null && message != null)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            } else {
                TextButton(enabled = !busy && pin.isNotEmpty() && (available ?: 0) > 0, onClick = {
                    busy = true
                    scope.launch {
                        message = try {
                            val n = container.vault.restore(pin)
                            done = true
                            String.format(restoredText, n)
                        } catch (e: WrongPinException) {
                            errorWrongPin
                        } catch (e: ApiException) {
                            errorNetwork
                        } catch (e: Exception) {
                            e.message ?: errorNetwork
                        }
                        busy = false
                    }
                }) { Text(stringResource(R.string.vault_restore_button)) }
            }
        },
        dismissButton = { if (!done && !busy) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Cloud status of one bot on the dashboard. */
@Composable
fun CloudCard(container: AppContainer, bot: StoredBot) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun upload() {
        busy = true
        error = false
        scope.launch {
            error = !container.vault.upload(bot.id)
            busy = false
        }
    }
    SectionCard(title = stringResource(R.string.vault_card_title)) {
        if (!container.cloudAvailable()) {
            Text(stringResource(R.string.vault_needs_login), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            CloudCardBody(bot, busy, error, onUpload = { if (container.vault.hasPin()) upload() else askPin = true }, onRemove = {
                busy = true
                scope.launch {
                    error = runCatching { container.vault.remove(bot.id) }.isFailure
                    busy = false
                }
            })
        }
    }
    if (askPin) {
        PinSetupDialog(onDismiss = { askPin = false }) { pin ->
            container.vault.setPin(pin)
            askPin = false
            upload()
        }
    }
}

@Composable
private fun CloudCardBody(bot: StoredBot, busy: Boolean, error: Boolean, onUpload: () -> Unit, onRemove: () -> Unit) {
        when (bot.cloud) {
            StoredBot.CLOUD_SYNCED -> Text(stringResource(R.string.vault_synced), color = PbColors.Green)
            StoredBot.CLOUD_FAILED -> Text(stringResource(R.string.vault_failed), color = PbColors.Yellow)
            else -> Text(stringResource(R.string.vault_local_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error) Text(stringResource(R.string.vault_error_network), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (busy) {
            CircularProgressIndicator()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bot.cloud == StoredBot.CLOUD_SYNCED) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.vault_remove), color = PbColors.Red) }
                } else {
                    TextButton(onClick = onUpload) {
                        Text(stringResource(if (bot.cloud == StoredBot.CLOUD_FAILED) R.string.vault_retry else R.string.vault_upload), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
}
