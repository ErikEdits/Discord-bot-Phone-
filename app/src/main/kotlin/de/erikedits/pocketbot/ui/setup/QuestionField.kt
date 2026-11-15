package de.erikedits.pocketbot.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.engine.NamedId
import de.erikedits.pocketbot.shared.catalog.Question
import de.erikedits.pocketbot.shared.catalog.QuestionType
import de.erikedits.pocketbot.shared.catalog.text
import de.erikedits.pocketbot.ui.theme.PbColors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Channels/roles of the running bot (null while offline). */
data class Pickers(val channels: List<NamedId>, val roles: List<NamedId>)

/** Renders one catalog question (SPEC-v2 5). */
@Composable
fun QuestionField(q: Question, value: JsonElement, lang: String, pickers: Pickers?, onChange: (JsonElement) -> Unit, onAskAi: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (q.type != QuestionType.BOOL) Text(q.label.pick(lang), fontWeight = FontWeight.SemiBold)
        when (q.type) {
            QuestionType.TEXT, QuestionType.EMOJI -> OutlinedTextField(
                value = value.text().orEmpty(), onValueChange = { onChange(JsonPrimitive(it)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            QuestionType.LONG_TEXT -> OutlinedTextField(
                value = value.text().orEmpty(), onValueChange = { onChange(JsonPrimitive(it)) },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            QuestionType.TEXT_LIST -> TextListField(value, onChange)
            QuestionType.CHOICE -> q.options.forEach { o ->
                Row(Modifier.fillMaxWidth().clickable { onChange(JsonPrimitive(o.value)) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = value.text() == o.value, onClick = null)
                    Text(o.label.pick(lang), modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
                }
            }
            QuestionType.MULTI_CHOICE -> {
                val selected = (value as? JsonArray)?.mapNotNull { it.text() }.orEmpty()
                q.options.forEach { o ->
                    val on = o.value in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange(JsonArray((if (on) selected - o.value else selected + o.value).map(::JsonPrimitive))) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Text(o.label.pick(lang), modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
            QuestionType.BOOL -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(q.label.pick(lang), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = (value as? JsonPrimitive)?.booleanOrNull == true, onCheckedChange = { onChange(JsonPrimitive(it)) })
            }
            QuestionType.NUMBER -> {
                var text by remember(value) { mutableStateOf((value as? JsonPrimitive)?.doubleOrNull?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
                OutlinedTextField(
                    value = text,
                    onValueChange = { t ->
                        text = t.filter { it.isDigit() || it == '-' }.take(9)
                        text.toLongOrNull()?.let { n ->
                            val clamped = n.coerceIn(q.min?.toLong() ?: Long.MIN_VALUE, q.max?.toLong() ?: Long.MAX_VALUE)
                            onChange(JsonPrimitive(clamped))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = if (q.min != null || q.max != null) ({ Text("${q.min?.toLong() ?: ""} – ${q.max?.toLong() ?: ""}") }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            QuestionType.CHANNEL -> NamedPicker(value, pickers?.channels, stringResource(R.string.q_channel_hint), onChange)
            QuestionType.ROLE -> NamedPicker(value, pickers?.roles, stringResource(R.string.q_role_hint), onChange)
            QuestionType.COLOR -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val hex = value.text().orEmpty()
                val c = hex.removePrefix("#").toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: PbColors.Grey
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(c))
                OutlinedTextField(value = hex, onValueChange = { onChange(JsonPrimitive(it.take(7))) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            QuestionType.TIME -> OutlinedTextField(
                value = value.text().orEmpty(), onValueChange = { onChange(JsonPrimitive(it.filter { c -> c.isDigit() || c == ':' }.take(5))) },
                singleLine = true, placeholder = { Text("18:00") }, modifier = Modifier.fillMaxWidth(),
            )
        }
        q.help.pick(lang).takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onAskAi != null && q.type in setOf(QuestionType.TEXT_LIST, QuestionType.LONG_TEXT, QuestionType.MULTI_CHOICE, QuestionType.CHOICE)) {
            TextButton(onClick = onAskAi) { Text(stringResource(R.string.q_ask_ai)) }
        }
    }
}

@Composable
private fun TextListField(value: JsonElement, onChange: (JsonElement) -> Unit) {
    val items = (value as? JsonArray)?.mapNotNull { it.text() } ?: value.text()?.let { listOf(it) }.orEmpty()
    var draft by remember { mutableStateOf("") }
    fun set(list: List<String>) = onChange(JsonArray(list.map(::JsonPrimitive)))
    items.forEachIndexed { i, item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = item,
                onValueChange = { new -> set(items.toMutableList().also { it[i] = new }) },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { set(items.toMutableList().also { it.removeAt(i) }) }) { Text("✕", color = PbColors.Red) }
        }
    }
    if (items.size < 25) Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft, onValueChange = { draft = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.q_list_add)) }, modifier = Modifier.weight(1f),
        )
        TextButton(enabled = draft.isNotBlank(), onClick = { set(items + draft.trim()); draft = "" }) { Text("+") }
    }
}

/** Name of a channel/role (created if missing) or a picked ID while the bot is online. */
@Composable
private fun NamedPicker(value: JsonElement, items: List<NamedId>?, hint: String, onChange: (JsonElement) -> Unit) {
    val current = value.text().orEmpty()
    val known = items?.firstOrNull { it.id == current }
    var open by remember { mutableStateOf(false) }
    if (!items.isNullOrEmpty()) {
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(known?.name ?: stringResource(R.string.q_pick_existing), modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                items.forEach { DropdownMenuItem(text = { Text(it.name) }, onClick = { open = false; onChange(JsonPrimitive(it.id)) }) }
            }
        }
    }
    OutlinedTextField(
        value = if (known != null) "" else current,
        onValueChange = { onChange(JsonPrimitive(it)) },
        singleLine = true,
        placeholder = { Text(if (known != null) known.name else "") },
        supportingText = { Text(hint) },
        modifier = Modifier.fillMaxWidth(),
    )
}
