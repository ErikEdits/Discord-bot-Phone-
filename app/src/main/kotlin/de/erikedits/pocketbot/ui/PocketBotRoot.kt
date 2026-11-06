package de.erikedits.pocketbot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.data.AuthState
import de.erikedits.pocketbot.ui.editor.RuleEditorScreen
import de.erikedits.pocketbot.ui.editor.RulesScreen
import de.erikedits.pocketbot.ui.screens.BotListScreen
import de.erikedits.pocketbot.ui.screens.CreateBotScreen
import de.erikedits.pocketbot.ui.screens.DashboardScreen
import de.erikedits.pocketbot.ui.screens.LoginScreen
import de.erikedits.pocketbot.ui.screens.SettingsScreen
import de.erikedits.pocketbot.ui.screens.TermsScreen

/** Terms → Discord login → app (SPEC 2.1). */
@Composable
fun PocketBotRoot(container: AppContainer) {
    var termsAccepted by remember { mutableStateOf(container.prefs.termsAccepted) }
    val auth by container.auth.state.collectAsStateWithLifecycle()
    when {
        !termsAccepted -> TermsScreen(onAccept = {
            container.prefs.termsAccepted = true
            termsAccepted = true
        })
        auth !is AuthState.LoggedIn -> LoginScreen(container, auth)
        else -> MainNav(container)
    }
}

@Composable
private fun MainNav(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "bots") {
        composable("bots") {
            BotListScreen(
                container = container,
                onCreate = { nav.navigate("create") },
                onOpen = { id -> nav.navigate("bot/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("create") {
            CreateBotScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onDone = { id ->
                    nav.navigate("bot/$id") { popUpTo("bots") }
                },
            )
        }
        composable("bot/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DashboardScreen(
                container = container,
                botId = id,
                onBack = { nav.popBackStack() },
                onOpenRules = { nav.navigate("bot/$id/rules") },
            )
        }
        composable("bot/{id}/rules", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            RulesScreen(container, id, onBack = { nav.popBackStack() }, onEdit = { ruleId -> nav.navigate("bot/$id/rule/$ruleId") })
        }
        composable(
            "bot/{id}/rule/{ruleId}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("ruleId") { type = NavType.StringType }),
        ) { entry ->
            RuleEditorScreen(
                container,
                entry.arguments?.getString("id").orEmpty(),
                entry.arguments?.getString("ruleId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}
