package de.erikedits.pocketbot.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.shared.config.Action
import de.erikedits.pocketbot.shared.config.OptionType
import de.erikedits.pocketbot.shared.config.Rule
import de.erikedits.pocketbot.shared.config.Trigger
import de.erikedits.pocketbot.shared.config.isValidCommandName
import de.erikedits.pocketbot.ui.theme.PbColors

/** WENN → FALLS → DANN editor for one rule (SPEC 3.1), cards stacked vertically. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(container: AppContainer, botId: String, ruleId: String, onBack: () -> Unit) {
    val config = remember { container.configs.get(botId) }
    val original = remember { config.rules.firstOrNull { it.id == ruleId } }
    var rule by remember { mutableStateOf(original ?: Rule(name = "", trigger = Trigger.SlashCommand("befehl"), actions = listOf(Action.Reply("Hallo {user}!")))) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val pickers = remember {
        val snap = container.botManager.engine(botId)?.snapshot().orEmpty()
        PickerData(
            channels = snap.flatMap { g -> g.channels.map { it.copy(name = if (snap.size > 1) "${it.name} (${g.name})" else it.name) } },
            roles = snap.flatMap { g -> g.roles.map { it.copy(name = if (snap.size > 1) "${it.name} (${g.name})" else it.name) } },
        )
    }
    val userOptions = (rule.trigger as? Trigger.SlashCommand)?.options?.filter { it.type == OptionType.USER }?.map { it.name }.orEmpty()
    val nameError = (rule.trigger as? Trigger.SlashCommand)?.let { !isValidCommandName(it.name) } == true
    val duplicate = (rule.trigger as? Trigger.SlashCommand)?.let { t ->
        config.rules.any { it.id != rule.id && (it.trigger as? Trigger.SlashCommand)?.name == t.name }
    } == true

    fun save() {
        val named = if (rule.name.isBlank()) rule.copy(name = (rule.trigger as? Trigger.SlashCommand)?.let { "/" + it.name } ?: "Funktion") else rule
        val rules = if (original == null) config.rules + named else config.rules.map { if (it.id == named.id) named else it }
        container.botManager.saveConfig(botId, config.copy(rules = rules))
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (original == null) R.string.editor_new_title else R.string.editor_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (rule != original) confirmDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.dashboard_delete)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextInput(stringResource(R.string.editor_rule_name), rule.name, { rule = rule.copy(name = it.take(60)) })
            SwitchRow(stringResource(R.string.editor_enabled), rule.enabled) { rule = rule.copy(enabled = it) }

            // WENN
            BlockCard(stringResource(R.string.editor_when), PbColors.Blurple) {
                ChoiceField(
                    stringResource(R.string.editor_trigger_type),
                    TRIGGER_TYPES.map { it to typeLabel(it) },
                    triggerKey(rule.trigger),
                    { key -> if (key != triggerKey(rule.trigger)) rule = rule.copy(trigger = newTrigger(key)) },
                )
                TriggerFields(rule.trigger, pickers) { rule = rule.copy(trigger = it) }
                if (nameError) Text(stringResource(R.string.editor_invalid_name), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (duplicate) Text(stringResource(R.string.editor_duplicate_command), color = PbColors.Yellow, style = MaterialTheme.typography.bodySmall)
            }

            // FALLS
            SectionLabel(stringResource(R.string.editor_if), PbColors.Yellow)
            rule.conditions.forEachIndexed { i, c ->
                BlockCard(typeLabel(conditionKey(c)), PbColors.Yellow, onRemove = { rule = rule.copy(conditions = rule.conditions.toMutableList().also { it.removeAt(i) }) }) {
                    ConditionFields(c, pickers) { n -> rule = rule.copy(conditions = rule.conditions.toMutableList().also { it[i] = n }) }
                }
            }
            AddMenu(stringResource(R.string.editor_add_condition), CONDITION_TYPES) { key -> rule = rule.copy(conditions = rule.conditions + newCondition(key)) }

            // DANN
            SectionLabel(stringResource(R.string.editor_then), PbColors.Green)
            ActionList(rule.actions, pickers, userOptions) { rule = rule.copy(actions = it) }

            // SONST
            if (rule.conditions.isNotEmpty()) {
                SectionLabel(stringResource(R.string.editor_else), PbColors.Red)
                ActionList(rule.elseActions, pickers, userOptions) { rule = rule.copy(elseActions = it) }
            }

            NumberInput(stringResource(R.string.editor_cooldown), rule.cooldownSeconds) { rule = rule.copy(cooldownSeconds = it.coerceIn(0, 7 * 86_400)) }
            Text(stringResource(R.string.editor_cooldown_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(onClick = ::save, enabled = !nameError && rule.actions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.editor_save))
            }
            if (rule.actions.isEmpty()) Text(stringResource(R.string.editor_need_action), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    container.botManager.saveConfig(botId, config.copy(rules = config.rules.filterNot { it.id == rule.id }))
                    onBack()
                }) { Text(stringResource(R.string.dashboard_delete), color = PbColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            confirmButton = { TextButton(onClick = { save() }) { Text(stringResource(R.string.editor_save)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text(stringResource(R.string.editor_discard)) } },
        )
    }
}

@Composable
private fun ActionList(actions: List<Action>, pickers: PickerData, userOptions: List<String>, onChange: (List<Action>) -> Unit) {
    actions.forEachIndexed { i, a ->
        BlockCard(
            "${i + 1}. " + typeLabel(actionKey(a)), PbColors.Green,
            onRemove = { onChange(actions.toMutableList().also { it.removeAt(i) }) },
            onUp = if (i > 0) ({ onChange(actions.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }) else null,
            onDown = if (i < actions.size - 1) ({ onChange(actions.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }) else null,
        ) {
            ActionFields(a, pickers, userOptions) { n -> onChange(actions.toMutableList().also { it[i] = n }) }
        }
    }
    AddMenu(stringResource(R.string.editor_add_action), ACTION_TYPES) { key -> onChange(actions + newAction(key)) }
}

@Composable
private fun AddMenu(label: String, keys: List<String>, onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            keys.forEach { key ->
                DropdownMenuItem(text = { Text(typeLabel(key)) }, onClick = { open = false; onAdd(key) })
            }
        }
    }
}

@Composable
private fun BlockCard(
    title: String,
    accent: Color,
    onRemove: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onUp != null) TextButton(onClick = onUp) { Text("▲") }
                if (onDown != null) TextButton(onClick = onDown) { Text("▼") }
                if (onRemove != null) TextButton(onClick = onRemove) { Text("✕", color = PbColors.Red) }
            }
            content()
        }
    }
}

