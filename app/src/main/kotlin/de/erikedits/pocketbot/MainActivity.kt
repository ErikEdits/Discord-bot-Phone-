package de.erikedits.pocketbot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import de.erikedits.pocketbot.ui.PocketBotRoot
import de.erikedits.pocketbot.ui.theme.PocketBotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as PocketBotApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PocketBotTheme {
                PocketBotRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Came back from the Custom Tab without a redirect -> stop the spinner.
        container.auth.cancelPending()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "pocketbot" && uri.host == "auth") {
            intent.data = null
            lifecycleScope.launch { container.auth.handleRedirect(uri) }
        }
    }
}
