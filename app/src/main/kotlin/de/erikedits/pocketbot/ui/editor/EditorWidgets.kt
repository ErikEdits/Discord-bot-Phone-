package de.erikedits.pocketbot.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.engine.NamedId

/** Dropdown chooser without experimental APIs. */
@Composable
fun <T> ChoiceField(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(options.firstOrNull { it.first == selected }?.second ?: "–", modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
                }
            }
        }
    }
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, multiline: Boolean = false, number: Boolean = false, placeholders: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = !multiline,
            minLines = if (multiline) 2 else 1,
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        if (placeholders) PlaceholderButton { onChange(value + it) }
    }
}

@Composable
fun NumberInput(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t.filter(Char::isDigit).take(7); text.toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** ID field for channels/roles with a picker filled from the running bot. */
@Composable
fun IdPicker(label: String, value: String, items: List<NamedId>, emptyHint: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val known = items.firstOrNull { it.id == value }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    known?.name ?: value.ifBlank { stringResource(R.string.editor_pick) },
                    modifier = Modifier.weight(1f),
                    fontWeight = if (known != null) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (items.isEmpty()) {
                    DropdownMenuItem(text = { Text(emptyHint) }, onClick = { open = false })
                }
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = { open = false; onChange(item.id) })
                }
            }
        }
        if (items.isEmpty()) {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it.filter(Char::isDigit)) },
                label = { Text(stringResource(R.string.editor_id_manual)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

val PLACEHOLDERS = listOf(
    "{user}", "{user.mention}", "{user.name}", "{server}", "{members}", "{channel}", "{channel.mention}",
    "{arg.name}", "{var.muenzen}", "{servervar.zaehler}", "{random:1-6}", "{choice:a|b|c}", "{date}", "{time}",
)

@Composable
fun PlaceholderButton(onInsert: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.editor_placeholders)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PLACEHOLDERS.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = { open = false; onInsert(p) })
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun RowButtons(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
