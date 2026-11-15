package de.erikedits.pocketbot.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.shared.catalog.BotSetup

/** Change the bot types and answers of an existing bot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(container: AppContainer, botId: String, onBack: () -> Unit) {
    var setupJson by rememberSaveable { mutableStateOf(container.setups.get(botId).toJson()) }
    val setup = remember(setupJson) { BotSetup.fromJson(setupJson) }
    var phase by rememberSaveable { mutableIntStateOf(0) }
    val pickers = remember {
        val snap = container.botManager.engine(botId)?.snapshot().orEmpty()
        if (snap.isEmpty()) null else Pickers(snap.flatMap { it.channels }, snap.flatMap { it.roles })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_edit_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (phase == 0) {
                DescribeStep(container, setup, onSetup = { setupJson = it.toJson() }, onNext = { phase = 1 }, nextLabel = stringResource(R.string.next))
            } else {
                QuestionsStep(
                    container, setup, pickers,
                    onSetup = { setupJson = it.toJson() },
                    onBackFromFirst = { phase = 0 },
                    onDone = { container.botManager.saveSetup(botId, setup); onBack() },
                    doneLabel = stringResource(R.string.setup_save),
                )
            }
        }
    }
}
