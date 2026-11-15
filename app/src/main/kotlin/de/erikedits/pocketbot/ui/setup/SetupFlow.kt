package de.erikedits.pocketbot.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.shared.api.AiRequest
import de.erikedits.pocketbot.shared.api.ApiException
import de.erikedits.pocketbot.shared.catalog.BotSetup
import de.erikedits.pocketbot.shared.catalog.BotType
import de.erikedits.pocketbot.shared.catalog.Catalog
import de.erikedits.pocketbot.shared.catalog.Question
import de.erikedits.pocketbot.shared.catalog.Questions
import de.erikedits.pocketbot.shared.catalog.TypeSelection
import de.erikedits.pocketbot.ui.screens.SectionCard
import de.erikedits.pocketbot.ui.screens.cloudAvailable
import de.erikedits.pocketbot.ui.theme.PbColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

@Composable
fun currentLanguage(): String = LocalConfiguration.current.locales[0].language

/** Applies an AI result to a setup: known types only, answers checked against the catalog. */
fun applyAi(setup: BotSetup, catalog: Catalog, types: List<String>, answers: Map<String, Map<String, JsonElement>>): BotSetup {
    var s = setup
    types.mapNotNull(catalog::type).forEach { type ->
        val existing = s.types.firstOrNull { it.typeId == type.id }
        val clean = Questions.sanitizeAll(type, answers[type.id].orEmpty())
        s = s.withType(TypeSelection(type.id, type.version, (existing?.answers.orEmpty()) + clean))
    }
    return s
}

private fun aiErrorText(code: String): Int = when (code) {
    "ai_limit", "rate_limited" -> R.string.ai_error_limit
    "network" -> R.string.login_error_network
    "unauthorized" -> R.string.ai_error_login
    else -> R.string.ai_error_unavailable
}

/** Asks once whether texts may be sent to the AI provider (SPEC-v2 4). */
@Composable
fun AiConsentDialog(onResult: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onResult(false) },
        title = { Text(stringResource(R.string.ai_consent_title)) },
        text = { Text(stringResource(R.string.ai_consent_text)) },
        confirmButton = { TextButton(onClick = { onResult(true) }) { Text(stringResource(R.string.ai_consent_yes)) } },
        dismissButton = { TextButton(onClick = { onResult(false) }) { Text(stringResource(R.string.ai_consent_no)) } },
    )
}

/** Step "What should your bot do?": free text (AI) and/or picking bot types. */
@Composable
fun DescribeStep(container: AppContainer, setup: BotSetup, onSetup: (BotSetup) -> Unit, onNext: () -> Unit, nextLabel: String) {
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var askConsent by remember { mutableStateOf(false) }
    val aiPossible = container.cloudAvailable()

    fun runAi() {
        busy = true
        error = null
        scope.launch {
            try {
                val result = container.api.aiRun(AiRequest("setup", text.trim(), lang, setup.types.map { it.typeId }))
                onSetup(applyAi(setup, catalog, result.types, result.answers))
                summary = result.summary
                if (result.types.isEmpty()) error = R.string.ai_nothing_found
            } catch (e: ApiException) {
                error = aiErrorText(e.code)
            }
            busy = false
        }
    }

    Text(stringResource(R.string.describe_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.describe_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = text, onValueChange = { text = it.take(2000) }, minLines = 4,
        placeholder = { Text(stringResource(R.string.describe_placeholder)) }, modifier = Modifier.fillMaxWidth(),
    )
    if (aiPossible) {
        Button(
            enabled = text.isNotBlank() && !busy,
            onClick = { if (container.prefs.aiConsent) runAi() else askConsent = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.describe_ai_button)) }
    } else {
        Text(stringResource(R.string.ai_needs_login), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.describe_ai_working))
    }
    error?.let { Text(stringResource(it), color = PbColors.Yellow, style = MaterialTheme.typography.bodySmall) }
    summary?.let { SectionCard(title = stringResource(R.string.describe_ai_summary)) { Text(it) } }

    Text(stringResource(R.string.describe_pick), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    catalog.index.types.forEach { entry ->
        val type = catalog.type(entry.id) ?: return@forEach
        val selected = setup.types.any { it.typeId == entry.id }
        Card(
            Modifier.fillMaxWidth().clickable {
                onSetup(if (selected) setup.copy(types = setup.types.filterNot { it.typeId == entry.id }) else setup.withType(TypeSelection(type.id, type.version)))
            },
            colors = CardDefaults.cardColors(containerColor = if (selected) PbColors.BlurpleDark else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.emoji, style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(entry.name.pick(lang), fontWeight = FontWeight.SemiBold)
                    Text(entry.description.pick(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(checked = selected, onCheckedChange = null)
            }
        }
    }
    Button(onClick = onNext, enabled = setup.types.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) { Text(nextLabel) }

    if (askConsent) {
        AiConsentDialog { ok ->
            askConsent = false
            container.prefs.aiConsent = ok
            if (ok) runAi()
        }
    }
}

/** One page per chosen bot type with its (visible) questions, prefilled with defaults/AI answers. */
@Composable
fun QuestionsStep(
    container: AppContainer,
    setup: BotSetup,
    pickers: Pickers?,
    onSetup: (BotSetup) -> Unit,
    onBackFromFirst: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String,
) {
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()
    val lang = currentLanguage()
    val types: List<Pair<TypeSelection, BotType>> = setup.types.mapNotNull { sel -> catalog.type(sel.typeId)?.let { sel to it } }
    var page by rememberSaveable { mutableIntStateOf(0) }
    if (types.isEmpty()) {
        Text(stringResource(R.string.questions_none))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(doneLabel) }
        return
    }
    page = page.coerceIn(0, types.size - 1)
    val (sel, type) = types[page]
    var aiQuestion by remember { mutableStateOf<Question?>(null) }

    fun setAnswer(id: String, v: JsonElement) = onSetup(setup.withType(sel.copy(answers = sel.answers + (id to v))))

    LinearProgressIndicator(progress = { (page + 1f) / types.size }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.questions_page, page + 1, types.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(type.emoji, style = MaterialTheme.typography.headlineMedium)
        Text(type.name.pick(lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
    Text(type.description.pick(lang), color = MaterialTheme.colorScheme.onSurfaceVariant)

    var lastGroup: String? = null
    Questions.visible(type, sel.answers).forEach { q ->
        val group = q.group?.pick(lang)
        if (group != null && group != lastGroup) {
            Text(group, style = MaterialTheme.typography.titleMedium, color = PbColors.Blurple, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            lastGroup = group
        }
        SectionCard {
            QuestionField(
                q, Questions.valueOf(q, sel.answers), lang, pickers,
                onChange = { setAnswer(q.id, it) },
                onAskAi = if (container.cloudAvailable()) ({ aiQuestion = q }) else null,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (page == 0) onBackFromFirst() else page-- }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.back)) }
        Button(onClick = { if (page < types.size - 1) page++ else onDone() }, modifier = Modifier.weight(1f)) {
            Text(if (page < types.size - 1) stringResource(R.string.next) else doneLabel)
        }
    }

    aiQuestion?.let { q -> AiQuestionDialog(container, type, q, lang, onDismiss = { aiQuestion = null }) { v -> setAnswer(q.id, v); aiQuestion = null } }
}

/** Answer one question in your own words; the AI turns it into a valid answer. */
@Composable
private fun AiQuestionDialog(container: AppContainer, type: BotType, q: Question, lang: String, onDismiss: () -> Unit, onAnswer: (JsonElement) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var askConsent by remember { mutableStateOf(!container.prefs.aiConsent) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(q.label.pick(lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, minLines = 3, placeholder = { Text(stringResource(R.string.q_ai_placeholder)) }, modifier = Modifier.fillMaxWidth())
                if (busy) CircularProgressIndicator()
                error?.let { Text(stringResource(it), color = PbColors.Yellow, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val r = container.api.aiRun(AiRequest("question", text.trim(), lang, listOf(type.id), q.id))
                        val v = r.answers[type.id]?.get(q.id)?.let { Questions.sanitize(q, it) }
                        if (v != null) onAnswer(v) else error = R.string.ai_nothing_found
                    } catch (e: ApiException) {
                        error = aiErrorText(e.code)
                    }
                    busy = false
                }
            }) { Text(stringResource(R.string.q_ai_apply)) }
        },
        dismissButton = { if (!busy) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (askConsent) AiConsentDialog { ok -> container.prefs.aiConsent = ok; askConsent = false; if (!ok) onDismiss() }
}
