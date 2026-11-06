package de.erikedits.pocketbot.ui.editor

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.Templates
import de.erikedits.pocketbot.shared.config.requiredIntents
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import de.erikedits.pocketbot.ui.screens.SectionCard
import de.erikedits.pocketbot.ui.screens.categoryLabel
import de.erikedits.pocketbot.ui.theme.PbColors

/** All functions (rules) of one bot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(container: AppContainer, botId: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val context = LocalContext.current
    val configs by container.configs.configs.collectAsStateWithLifecycle()
    val initialConfig = remember(botId) { container.configs.get(botId) }
    val config = configs[botId] ?: initialConfig
    var showTemplates by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, config.toJson())
                        context.startActivity(Intent.createChooser(send, null))
                    }) { Icon(Icons.Default.Share, stringResource(R.string.rules_export)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit("new") },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.rules_new)) },
                containerColor = PbColors.Blurple,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedButton(onClick = { showTemplates = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rules_add_template)) }
            }
            val intents = config.requiredIntents()
            if (intents.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.rules_intents_title)) {
                        if (PrivilegedIntent.MESSAGE_CONTENT in intents) Text("• " + stringResource(R.string.intent_message_content))
                        if (PrivilegedIntent.SERVER_MEMBERS in intents) Text("• " + stringResource(R.string.intent_server_members))
                        Text(stringResource(R.string.intents_how), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow)
                    }
                }
            }
            if (config.rules.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧩", style = MaterialTheme.typography.displayMedium)
                        Text(stringResource(R.string.rules_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(config.rules, key = { it.id }) { rule ->
                Card(
                    Modifier.fillMaxWidth().clickable { onEdit(rule.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name.ifBlank { "–" }, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.rules_summary, triggerSummary(rule.trigger), rule.actions.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { on ->
                            container.botManager.saveConfig(botId, config.copy(rules = config.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }))
                        })
                    }
                }
            }
        }
    }

    if (showTemplates) {
        TemplateDialog(onDismiss = { showTemplates = false }) { keys, language ->
            var merged: BotConfig = config
            keys.forEach { merged = Templates.merge(merged, Templates.forCategory(it, language)) }
            container.botManager.saveConfig(botId, merged)
            showTemplates = false
        }
    }
}

@Composable
fun TemplateDialog(onDismiss: () -> Unit, onAdd: (List<String>, String) -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_add_template)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Templates.categories.forEach { key ->
                    val template = remember(key, language) { Templates.forCategory(key, language) }
                    Row(
                        Modifier.fillMaxWidth().clickable { if (key in selected) selected.remove(key) else selected.add(key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = key in selected, onCheckedChange = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(stringResource(categoryLabel(key)), fontWeight = FontWeight.SemiBold)
                            Text(
                                template.rules.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = selected.isNotEmpty(), onClick = { onAdd(selected.toList(), language) }) { Text(stringResource(R.string.rules_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
