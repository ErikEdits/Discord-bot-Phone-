package de.erikedits.pocketbot.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.BuildConfig
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.data.AuthState
import de.erikedits.pocketbot.ui.theme.PbColors

@Composable
fun LoginScreen(container: AppContainer, state: AuthState) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            InitialAvatar("P", size = 88.dp)
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.login_text), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (state == AuthState.Working) {
                CircularProgressIndicator()
                Text(stringResource(R.string.login_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(
                    onClick = {
                        val url = container.auth.beginLogin()
                        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PbColors.Blurple),
                ) { Text(stringResource(R.string.login_button)) }
            }
            if (state is AuthState.Failed) {
                Text(
                    stringResource(loginErrorText(state.code)),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { container.auth.dismissError() }) { Text(stringResource(R.string.ok)) }
            }
            if (!container.apiConfigured) {
                Text(stringResource(R.string.login_backend_missing), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow, textAlign = TextAlign.Center)
            }
            if (BuildConfig.DEBUG) {
                TextButton(onClick = { container.auth.devLogin() }) { Text(stringResource(R.string.login_dev_skip)) }
            }
        }
    }
}

private fun loginErrorText(code: String): Int = when (code) {
    "access_denied" -> R.string.login_error_denied
    "backend_offline", "maintenance" -> R.string.login_error_offline
    "network" -> R.string.login_error_network
    "expired", "invalid_code", "state_mismatch" -> R.string.login_error_expired
    "banned" -> R.string.login_error_banned
    else -> R.string.login_error_generic
}
