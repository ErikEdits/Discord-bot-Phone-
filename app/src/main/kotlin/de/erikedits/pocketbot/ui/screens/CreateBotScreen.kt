package de.erikedits.pocketbot.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.data.StoredBot
import de.erikedits.pocketbot.service.BotRuntimeService
import de.erikedits.pocketbot.shared.api.DefaultHosters
import de.erikedits.pocketbot.shared.api.Hoster
import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.Templates
import de.erikedits.pocketbot.shared.config.requiredIntents
import de.erikedits.pocketbot.shared.discord.BotIdentity
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import de.erikedits.pocketbot.shared.discord.missing
import de.erikedits.pocketbot.shared.discord.BotToken
import de.erikedits.pocketbot.shared.discord.InviteLink
import de.erikedits.pocketbot.shared.discord.TokenCheckResult
import de.erikedits.pocketbot.shared.util.HosterAdvice
import de.erikedits.pocketbot.ui.theme.PbColors
import kotlinx.coroutines.launch

private enum class Step { TUTORIAL, TOKEN, STORAGE, PURPOSE, INVITE }

private val CATEGORIES = listOf(
    "moderation" to R.string.purpose_cat_moderation,
    "community" to R.string.purpose_cat_community,
    "support" to R.string.purpose_cat_support,
    "gaming" to R.string.purpose_cat_gaming,
    "other" to R.string.purpose_cat_other,
)

/** Bot creation wizard (SPEC 2.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBotScreen(container: AppContainer, onBack: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(Step.TUTORIAL) }
    var token by rememberSaveable { mutableStateOf("") }
    var identity by remember { mutableStateOf<BotIdentity?>(null) }
    var multiServer by rememberSaveable { mutableStateOf(false) }
    var useCloud by rememberSaveable { mutableStateOf(false) }
    var newPin by remember { mutableStateOf<String?>(null) }
    val categories = remember { mutableStateListOf<String>() }
    var useTemplate by rememberSaveable { mutableStateOf(true) }
    val language = LocalConfiguration.current.locales[0].language

    fun back() {
        if (step == Step.TUTORIAL) onBack() else step = Step.entries[step.ordinal - 1]
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title)) },
                navigationIcon = {
                    IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { (step.ordinal + 1f) / Step.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.create_step_of, step.ordinal + 1, Step.entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (step) {
                    Step.TUTORIAL -> TutorialStep(onNext = { step = Step.TOKEN })
                    Step.TOKEN -> TokenStep(
                        container = container,
                        token = token,
                        onTokenChange = { token = it; identity = null },
                        identity = identity,
                        onChecked = { identity = it },
                        onNext = { step = Step.STORAGE },
                    )
                    Step.STORAGE -> StorageStep(
                        container = container,
                        useCloud = useCloud,
                        onUseCloud = { useCloud = it },
                        onValidPin = { newPin = it },
                        canContinue = !useCloud || container.vault.hasPin() || newPin != null,
                        onNext = { step = Step.PURPOSE },
                    )
                    Step.PURPOSE -> PurposeStep(
                        identity = identity,
                        multiServer = multiServer,
                        onMultiServer = { multiServer = it },
                        categories = categories,
                        useTemplate = useTemplate,
                        onUseTemplate = { useTemplate = it },
                        language = language,
                        onNext = { step = Step.INVITE },
                    )
                    Step.INVITE -> {
                        val bot = identity
                        if (bot == null) {
                            // Identity is not kept across process death -> check the token again.
                            LaunchedEffect(Unit) { step = Step.TOKEN }
                        } else {
                            InviteStep(bot) {
                                val cleaned = BotToken.clean(token)
                                container.bots.upsert(
                                    StoredBot(
                                        id = bot.applicationId,
                                        name = bot.username,
                                        avatarUrl = bot.avatarUrl,
                                        multiServer = multiServer,
                                        categories = categories.toList(),
                                    ),
                                    token = cleaned,
                                )
                                container.botManager.remove(bot.applicationId) // token may have changed
                                if (useTemplate && categories.isNotEmpty()) {
                                    var config = container.configs.get(bot.applicationId)
                                    categories.forEach { config = Templates.merge(config, Templates.forCategory(it, language)) }
                                    container.configs.save(bot.applicationId, config)
                                }
                                BotRuntimeService.startBot(context, bot.applicationId)
                                if (useCloud) {
                                    newPin?.let { container.vault.setPin(it) }
                                    container.appScope.launch { container.vault.upload(bot.applicationId) }
                                }
                                onDone(bot.applicationId)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStep(onNext: () -> Unit) {
    val context = LocalContext.current
    Text(stringResource(R.string.tutorial_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.tutorial_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
    val steps = listOf(
        R.string.tutorial_step1, R.string.tutorial_step2, R.string.tutorial_step3,
        R.string.tutorial_step4, R.string.tutorial_step5, R.string.tutorial_step6,
    )
    steps.forEachIndexed { i, res ->
        SectionCard(title = stringResource(R.string.tutorial_step_label, i + 1)) {
            Text(stringResource(res))
        }
    }
    OutlinedButton(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/developers/applications"))) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.tutorial_open_portal)) }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tutorial_have_token)) }
}

@Composable
private fun TokenStep(
    container: AppContainer,
    token: String,
    onTokenChange: (String) -> Unit,
    identity: BotIdentity?,
    onChecked: (BotIdentity) -> Unit,
    onNext: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var errorDetail by remember { mutableStateOf("") }

    Text(stringResource(R.string.token_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.token_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = token,
        onValueChange = { onTokenChange(it); error = null },
        label = { Text(stringResource(R.string.token_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clipboard.getText()?.text?.let { onTokenChange(BotToken.clean(it)); error = null } }) {
            Text(stringResource(R.string.token_paste))
        }
        Button(
            enabled = token.isNotBlank() && !checking,
            onClick = {
                checking = true
                error = null
                scope.launch {
                    when (val result = container.discord.checkToken(token)) {
                        is TokenCheckResult.Valid -> onChecked(result.bot)
                        TokenCheckResult.Invalid -> error = R.string.token_error_invalid
                        TokenCheckResult.Malformed -> error = R.string.token_error_malformed
                        is TokenCheckResult.Failed -> { error = R.string.token_error_network; errorDetail = result.message }
                    }
                    checking = false
                }
            },
        ) { Text(stringResource(R.string.token_check)) }
        if (checking) CircularProgressIndicator(Modifier.align(Alignment.CenterVertically))
    }
    error?.let {
        Text(stringResource(it, errorDetail), color = MaterialTheme.colorScheme.error)
    }
    if (identity != null) {
        SectionCard(title = stringResource(R.string.token_valid_title)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(identity.username, identity.avatarUrl)
                Column {
                    Text(identity.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.token_valid_servers, identity.guildCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SectionCard(title = stringResource(R.string.intents_title)) {
            Text(stringResource(R.string.intents_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IntentRow(stringResource(R.string.intent_message_content), identity.intents.messageContent)
            IntentRow(stringResource(R.string.intent_server_members), identity.intents.serverMembers)
            IntentRow(stringResource(R.string.intent_presence), identity.intents.presence)
            if (!identity.intents.messageContent || !identity.intents.serverMembers) {
                Text(stringResource(R.string.intents_how), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = {
                checking = true
                scope.launch {
                    (container.discord.checkToken(token) as? TokenCheckResult.Valid)?.let { onChecked(it.bot) }
                    checking = false
                }
            }) { Text(stringResource(R.string.intents_recheck)) }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.next)) }
    }
}

@Composable
private fun IntentRow(label: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (on) "✅" else "⚪")
        Text(label, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (on) R.string.intent_on else R.string.intent_off),
            color = if (on) PbColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StorageStep(
    container: AppContainer,
    useCloud: Boolean,
    onUseCloud: (Boolean) -> Unit,
    onValidPin: (String?) -> Unit,
    canContinue: Boolean,
    onNext: () -> Unit,
) {
    val cloudPossible = container.cloudAvailable()
    Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    SectionCard {
        Row(
            Modifier.fillMaxWidth().selectable(selected = !useCloud, onClick = { onUseCloud(false) }, role = Role.RadioButton),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = !useCloud, onClick = null)
            Column(Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.storage_local), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.storage_local_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    SectionCard {
        Row(
            Modifier.fillMaxWidth().selectable(selected = useCloud, enabled = cloudPossible, onClick = { onUseCloud(true) }, role = Role.RadioButton),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = useCloud, onClick = null, enabled = cloudPossible)
            Column(Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.storage_cloud), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(if (cloudPossible) R.string.storage_cloud_text else R.string.vault_needs_login),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (useCloud) {
            if (container.vault.hasPin()) {
                Text(stringResource(R.string.vault_pin_already_set), style = MaterialTheme.typography.bodySmall, color = PbColors.Green)
            } else {
                PinSetupFields(onValidPin)
            }
        }
    }
    Button(onClick = onNext, enabled = canContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.next)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurposeStep(
    identity: BotIdentity?,
    multiServer: Boolean,
    onMultiServer: (Boolean) -> Unit,
    categories: MutableList<String>,
    useTemplate: Boolean,
    onUseTemplate: (Boolean) -> Unit,
    language: String,
    onNext: () -> Unit,
) {
    Text(stringResource(R.string.purpose_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    SectionCard {
        listOf(false to R.string.purpose_own_server, true to R.string.purpose_multi_server).forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = multiServer == value, onClick = { onMultiServer(value) }, role = Role.RadioButton).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = multiServer == value, onClick = null)
                Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    val recommend = HosterAdvice.shouldRecommend(multiServer, identity?.guildCount ?: 0, 0)
    if (recommend) HosterRecommendation()

    Text(stringResource(R.string.purpose_what), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.purpose_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CATEGORIES.forEach { (key, label) ->
            FilterChip(
                selected = key in categories,
                onClick = { if (key in categories) categories.remove(key) else categories.add(key) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    if (categories.isNotEmpty()) {
        val templateConfig = remember(categories.toList(), language) {
            var c = BotConfig()
            categories.forEach { c = Templates.merge(c, Templates.forCategory(it, language)) }
            c
        }
        SectionCard {
            Row(
                Modifier.fillMaxWidth().selectable(selected = useTemplate, onClick = { onUseTemplate(!useTemplate) }, role = Role.Checkbox),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = useTemplate, onCheckedChange = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.purpose_use_template, templateConfig.rules.size), fontWeight = FontWeight.SemiBold)
                    Text(templateConfig.rules.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val missing = identity?.intents?.missing(templateConfig.requiredIntents()).orEmpty()
            if (useTemplate && missing.isNotEmpty()) {
                Text(stringResource(R.string.purpose_template_intents), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow)
                if (PrivilegedIntent.MESSAGE_CONTENT in missing) Text("• " + stringResource(R.string.intent_message_content), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow)
                if (PrivilegedIntent.SERVER_MEMBERS in missing) Text("• " + stringResource(R.string.intent_server_members), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow)
            }
        }
    }
    Text(stringResource(R.string.purpose_templates_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (recommend) R.string.purpose_phone_anyway else R.string.next))
    }
}

@Composable
private fun HosterRecommendation() {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    SectionCard(title = stringResource(R.string.hoster_title)) {
        Text(stringResource(R.string.hoster_text), style = MaterialTheme.typography.bodySmall)
        DefaultHosters.list.forEach { hoster: Hoster ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hoster.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(hoster.price.pick(language), style = MaterialTheme.typography.labelMedium, color = PbColors.Green)
                }
                Text(hoster.desc.pick(language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                hoster.url?.let { url ->
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Text(stringResource(R.string.hoster_open))
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteStep(bot: BotIdentity, onFinish: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val link = InviteLink.build(bot.applicationId)
    Text(stringResource(R.string.invite_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.invite_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionCard {
        Text(link, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(link)) }) { Text(stringResource(R.string.copy)) }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                context.startActivity(Intent.createChooser(send, null))
            }) { Text(stringResource(R.string.share)) }
            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }) { Text(stringResource(R.string.open)) }
        }
    }
    if (!bot.isPublic) {
        Text(stringResource(R.string.invite_private_hint), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow)
    }
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.invite_finish)) }
}
