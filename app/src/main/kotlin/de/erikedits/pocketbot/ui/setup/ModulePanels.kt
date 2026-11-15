package de.erikedits.pocketbot.ui.setup

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.engine.GuildInfo
import de.erikedits.pocketbot.engine.modules.ControlInput
import de.erikedits.pocketbot.engine.modules.InputKind
import de.erikedits.pocketbot.engine.modules.ModulePanel
import de.erikedits.pocketbot.engine.modules.PanelAction
import de.erikedits.pocketbot.shared.catalog.BotSetup
import de.erikedits.pocketbot.ui.screens.SectionCard
import de.erikedits.pocketbot.ui.theme.PbColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A pending app control: which module/action/item, waiting for confirm or input. */
private data class Pending(val typeId: String, val action: PanelAction, val item: String?)

/** Control cards for all modules of a running bot (SPEC-v2 3), refreshed every few seconds. */
@Composable
fun ModulePanels(container: AppContainer, botId: String) {
    val context = LocalContext.current
    val lang = currentLanguage()
    val scope = rememberCoroutineScope()
    val catalog = container.catalog.catalog.value
    var panels by remember { mutableStateOf<List<ModulePanel>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var image by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(botId, refresh) {
        while (true) {
            panels = withContext(Dispatchers.Default) { container.botManager.engine(botId)?.panels(lang).orEmpty() }
            delay(4000)
        }
    }

    fun run(p: Pending, input: ControlInput) {
        pending = null
        message = context.getString(R.string.panel_working)
        scope.launch {
            message = withContext(Dispatchers.IO) {
                container.botManager.engine(botId)?.perform(p.typeId, p.action.id, p.item, input, lang) ?: context.getString(R.string.panel_offline)
            }
            image = null
            refresh++
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val p = pending
        if (uri == null || p == null) { pending = null; return@rememberLauncherForActivityResult }
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null || bytes.size > 24 * 1024 * 1024) { pending = null; message = context.getString(R.string.panel_image_error); return@rememberLauncherForActivityResult }
        image = bytes to (displayName(context, uri) ?: "bild.jpg")
    }

    fun start(p: Pending) {
        pending = p
        if (p.action.input == InputKind.IMAGE_AND_CHANNEL) pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        else if (p.action.input == InputKind.NONE && p.action.confirm == null) run(p, ControlInput())
    }

    if (panels.isEmpty()) {
        SectionCard { Text(stringResource(R.string.panel_start_bot), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    message?.let { SectionCard { Text(it); TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } } }
    panels.forEach { panel ->
        val type = catalog.type(panel.typeId)
        SectionCard(title = "${type?.emoji ?: "🤖"} ${type?.name?.pick(lang) ?: panel.typeId}") {
            panel.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            panel.items.forEach { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row { item.actions.forEach { a -> TextButton(onClick = { start(Pending(panel.typeId, a, item.id)) }) { Text(a.label) } } }
                }
            }
            panel.actions.forEach { a ->
                OutlinedButton(onClick = { start(Pending(panel.typeId, a, null)) }, modifier = Modifier.fillMaxWidth()) { Text(a.label) }
            }
        }
    }

    val p = pending
    val guilds = remember(p) { container.botManager.engine(botId)?.snapshot().orEmpty() }
    when {
        p == null -> Unit
        p.action.input == InputKind.NONE && p.action.confirm != null -> ConfirmDialog(p.action.confirm!!, onDismiss = { pending = null }) { run(p, ControlInput()) }
        p.action.input == InputKind.GUILD -> GuildDialog(guilds, p.action.confirm, onDismiss = { pending = null }) { run(p, ControlInput(guildId = it.id)) }
        p.action.input == InputKind.TEXT -> TextDialog(p.action.label, onDismiss = { pending = null }) { run(p, ControlInput(text = it)) }
        p.action.input == InputKind.CHANNEL -> ChannelDialog(guilds, false, onDismiss = { pending = null }) { ch, _ -> run(p, ControlInput(channelId = ch)) }
        p.action.input == InputKind.IMAGE_AND_CHANNEL && image != null -> ChannelDialog(guilds, true, onDismiss = { pending = null; image = null }) { ch, caption ->
            run(p, ControlInput(channelId = ch, image = image!!.first, imageName = image!!.second, text = caption))
        }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

@Composable
private fun ConfirmDialog(text: String, onDismiss: () -> Unit, onOk: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onOk) { Text(stringResource(R.string.ok)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
)

@Composable
private fun GuildDialog(guilds: List<GuildInfo>, confirm: String?, onDismiss: () -> Unit, onPick: (GuildInfo) -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.panel_pick_server)) },
    text = {
        Column {
            confirm?.let { Text(it, color = PbColors.Yellow, modifier = Modifier.padding(bottom = 8.dp)) }
            if (guilds.isEmpty()) Text(stringResource(R.string.panel_offline))
            guilds.forEach { g -> Text(g.name, modifier = Modifier.fillMaxWidth().clickable { onPick(g) }.padding(vertical = 10.dp)) }
        }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
)

@Composable
private fun TextDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onOk(text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChannelDialog(guilds: List<GuildInfo>, withCaption: Boolean, onDismiss: () -> Unit, onPick: (String, String?) -> Unit) {
    var caption by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panel_pick_channel)) },
        text = {
            Column {
                if (withCaption) OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text(stringResource(R.string.panel_caption)) }, modifier = Modifier.fillMaxWidth())
                if (guilds.isEmpty()) Text(stringResource(R.string.panel_offline))
                guilds.forEach { g ->
                    Text(g.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    g.channels.forEach { ch -> Text(ch.name, modifier = Modifier.fillMaxWidth().clickable { onPick(ch.id, caption.ifBlank { null }) }.padding(vertical = 8.dp)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Images the image bot posts on schedule ("my collection"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageCollectionCard(container: AppContainer, botId: String) {
    val context = LocalContext.current
    val dir = remember(botId) { container.botManager.imageDir(botId) }
    var count by remember { mutableIntStateOf(dir.listFiles()?.size ?: 0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        dir.mkdirs()
        uris.forEachIndexed { i, uri ->
            runCatching {
                val name = "${System.currentTimeMillis()}_${i}_" + (displayName(context, uri) ?: "bild.jpg").replace(Regex("[^A-Za-z0-9._-]"), "_")
                context.contentResolver.openInputStream(uri)?.use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
            }
        }
        count = dir.listFiles()?.size ?: 0
    }
    SectionCard(title = stringResource(R.string.images_collection_title)) {
        Text(stringResource(R.string.images_collection_count, count))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.images_add)) }
            if (count > 0) TextButton(onClick = { dir.deleteRecursively(); count = 0 }) { Text(stringResource(R.string.images_clear), color = PbColors.Red) }
        }
    }
}

/** Rules from the old builder: still running, can be switched off or deleted (SPEC-v2 6). */
@Composable
fun LegacyRulesCard(container: AppContainer, botId: String, setup: BotSetup) {
    if (setup.legacyRules.isEmpty()) return
    SectionCard(title = stringResource(R.string.legacy_title)) {
        Text(stringResource(R.string.legacy_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        setup.legacyRules.forEach { rule ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.name.ifBlank { "–" }, modifier = Modifier.weight(1f))
                Switch(checked = rule.enabled, onCheckedChange = { on ->
                    container.botManager.saveSetup(botId, setup.copy(legacyRules = setup.legacyRules.map { if (it.id == rule.id) it.copy(enabled = on) else it }))
                })
                TextButton(onClick = { container.botManager.saveSetup(botId, setup.copy(legacyRules = setup.legacyRules.filterNot { it.id == rule.id })) }) {
                    Text("✕", color = PbColors.Red)
                }
            }
        }
    }
}
