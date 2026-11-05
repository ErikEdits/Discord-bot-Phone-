package de.erikedits.pocketbot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.data.StoredBot
import de.erikedits.pocketbot.service.BotRuntimeService
import de.erikedits.pocketbot.shared.discord.BotToken
import de.erikedits.pocketbot.shared.discord.TokenCheckResult
import kotlinx.coroutines.launch

/** Replace the token of an existing bot (after a reset in the Developer Portal or a storage problem). */
@Composable
fun TokenDialog(container: AppContainer, bot: StoredBot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val wrongBot = stringResource(R.string.token_dialog_wrong_bot, bot.name)
    val invalid = stringResource(R.string.token_error_invalid)
    val malformed = stringResource(R.string.token_error_malformed)
    val network = stringResource(R.string.token_error_network)
    val saveFailed = stringResource(R.string.token_dialog_save_failed)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.token_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.token_dialog_text, bot.name), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; error = null },
                    label = { Text(stringResource(R.string.token_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { token = BotToken.clean(it); error = null } }) {
                    Text(stringResource(R.string.token_paste))
                }
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = token.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    when (val result = container.discord.checkToken(token)) {
                        is TokenCheckResult.Valid -> {
                            if (result.bot.applicationId != bot.id) {
                                error = wrongBot
                            } else {
                                val cleaned = BotToken.clean(token)
                                val saved = runCatching { container.bots.setToken(bot.id, cleaned) }.isSuccess &&
                                    container.bots.token(bot.id) == cleaned
                                if (!saved) {
                                    error = saveFailed + "\n" + (container.bots.lastSecureError() ?: "")
                                } else {
                                    container.botManager.remove(bot.id)
                                    BotRuntimeService.startBot(context, bot.id)
                                    onDismiss()
                                }
                            }
                        }
                        TokenCheckResult.Invalid -> error = invalid
                        TokenCheckResult.Malformed -> error = malformed
                        is TokenCheckResult.Failed -> error = String.format(network, result.message)
                    }
                    busy = false
                }
            }) { Text(stringResource(R.string.token_dialog_save)) }
        },
        dismissButton = { if (!busy) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
