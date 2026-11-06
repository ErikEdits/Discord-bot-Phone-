package de.erikedits.pocketbot.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.engine.NamedId
import de.erikedits.pocketbot.shared.config.Action
import de.erikedits.pocketbot.shared.config.CommandOption
import de.erikedits.pocketbot.shared.config.CompareOp
import de.erikedits.pocketbot.shared.config.Condition
import de.erikedits.pocketbot.shared.config.MatchMode
import de.erikedits.pocketbot.shared.config.OptionType
import de.erikedits.pocketbot.shared.config.TARGET_USER
import de.erikedits.pocketbot.shared.config.Trigger
import de.erikedits.pocketbot.shared.config.VarScope
import de.erikedits.pocketbot.shared.config.sanitizeCommandName
import de.erikedits.pocketbot.ui.theme.PbColors

/** Channels/roles from the running bot for the pickers. */
data class PickerData(val channels: List<NamedId>, val roles: List<NamedId>)

// ------------------------------------------------------------- type lists

val TRIGGER_TYPES = listOf("slash", "prefix", "message", "member_join", "member_leave", "button", "interval")
val CONDITION_TYPES = listOf("has_role", "lacks_role", "is_admin", "not_admin", "in_channel", "variable", "chance")
val ACTION_TYPES = listOf(
    "reply", "send", "dm", "embed", "button_reply", "add_role", "remove_role", "timeout", "kick", "ban",
    "react", "delete_message", "var_add", "var_set", "wait", "notify_me",
)

fun triggerKey(t: Trigger) = when (t) {
    is Trigger.SlashCommand -> "slash"
    is Trigger.PrefixCommand -> "prefix"
    is Trigger.Message -> "message"
    Trigger.MemberJoin -> "member_join"
    Trigger.MemberLeave -> "member_leave"
    is Trigger.ButtonClick -> "button"
    is Trigger.Interval -> "interval"
}

fun newTrigger(key: String): Trigger = when (key) {
    "prefix" -> Trigger.PrefixCommand("befehl")
    "message" -> Trigger.Message("hallo")
    "member_join" -> Trigger.MemberJoin
    "member_leave" -> Trigger.MemberLeave
    "button" -> Trigger.ButtonClick("knopf")
    "interval" -> Trigger.Interval(60, "")
    else -> Trigger.SlashCommand("befehl", "")
}

fun conditionKey(c: Condition) = when (c) {
    is Condition.HasRole -> "has_role"
    is Condition.LacksRole -> "lacks_role"
    Condition.IsAdmin -> "is_admin"
    Condition.NotAdmin -> "not_admin"
    is Condition.InChannel -> "in_channel"
    is Condition.Variable -> "variable"
    is Condition.Chance -> "chance"
}

fun newCondition(key: String): Condition = when (key) {
    "has_role" -> Condition.HasRole()
    "lacks_role" -> Condition.LacksRole()
    "is_admin" -> Condition.IsAdmin
    "not_admin" -> Condition.NotAdmin
    "in_channel" -> Condition.InChannel()
    "variable" -> Condition.Variable(name = "muenzen")
    else -> Condition.Chance()
}

fun actionKey(a: Action) = when (a) {
    is Action.Reply -> "reply"
    is Action.Send -> "send"
    is Action.Dm -> "dm"
    is Action.Embed -> "embed"
    is Action.ButtonReply -> "button_reply"
    is Action.AddRole -> "add_role"
    is Action.RemoveRole -> "remove_role"
    is Action.Timeout -> "timeout"
    is Action.Kick -> "kick"
    is Action.Ban -> "ban"
    is Action.React -> "react"
    Action.DeleteMessage -> "delete_message"
    is Action.VarAdd -> "var_add"
    is Action.VarSet -> "var_set"
    is Action.Wait -> "wait"
    is Action.NotifyMe -> "notify_me"
}

fun newAction(key: String): Action = when (key) {
    "send" -> Action.Send()
    "dm" -> Action.Dm("Hallo {user}!")
    "embed" -> Action.Embed("Titel", "Text")
    "button_reply" -> Action.ButtonReply("Klick mich!", "OK", "knopf")
    "add_role" -> Action.AddRole()
    "remove_role" -> Action.RemoveRole()
    "timeout" -> Action.Timeout()
    "kick" -> Action.Kick()
    "ban" -> Action.Ban()
    "react" -> Action.React()
    "delete_message" -> Action.DeleteMessage
    "var_add" -> Action.VarAdd(name = "muenzen", amount = "10")
    "var_set" -> Action.VarSet(name = "muenzen", value = "0")
    "wait" -> Action.Wait()
    "notify_me" -> Action.NotifyMe("{user} hat etwas gemacht")
    else -> Action.Reply("Hallo {user}!")
}

@Composable
fun typeLabel(key: String): String = stringResource(
    when (key) {
        "slash" -> R.string.t_slash
        "prefix" -> R.string.t_prefix
        "message" -> R.string.t_message
        "member_join" -> R.string.t_member_join
        "member_leave" -> R.string.t_member_leave
        "button" -> R.string.t_button
        "interval" -> R.string.t_interval
        "has_role" -> R.string.c_has_role
        "lacks_role" -> R.string.c_lacks_role
        "is_admin" -> R.string.c_is_admin
        "not_admin" -> R.string.c_not_admin
        "in_channel" -> R.string.c_in_channel
        "variable" -> R.string.c_variable
        "chance" -> R.string.c_chance
        "reply" -> R.string.a_reply
        "send" -> R.string.a_send
        "dm" -> R.string.a_dm
        "embed" -> R.string.a_embed
        "button_reply" -> R.string.a_button_reply
        "add_role" -> R.string.a_add_role
        "remove_role" -> R.string.a_remove_role
        "timeout" -> R.string.a_timeout
        "kick" -> R.string.a_kick
        "ban" -> R.string.a_ban
        "react" -> R.string.a_react
        "delete_message" -> R.string.a_delete_message
        "var_add" -> R.string.a_var_add
        "var_set" -> R.string.a_var_set
        "wait" -> R.string.a_wait
        else -> R.string.a_notify_me
    },
)

/** One-line summary of a trigger for the rule list. */
@Composable
fun triggerSummary(t: Trigger): String = when (t) {
    is Trigger.SlashCommand -> "/" + t.name
    is Trigger.PrefixCommand -> "!" + t.name
    is Trigger.Message -> if (t.mode == MatchMode.ANY) stringResource(R.string.t_message_any) else typeLabel("message") + ": \"" + t.text + "\""
    else -> typeLabel(triggerKey(t))
}

// ------------------------------------------------------------- field editors

@Composable
fun TriggerFields(trigger: Trigger, pickers: PickerData, onChange: (Trigger) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (trigger) {
            is Trigger.SlashCommand -> {
                TextInput(stringResource(R.string.f_command_name), trigger.name, { onChange(trigger.copy(name = sanitizeCommandName(it))) })
                Text(stringResource(R.string.f_command_name_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextInput(stringResource(R.string.f_command_description), trigger.description, { onChange(trigger.copy(description = it.take(100))) })
                Text(stringResource(R.string.f_options), style = MaterialTheme.typography.titleSmall)
                trigger.options.forEachIndexed { i, o ->
                    OptionEditor(o, onChange = { n -> onChange(trigger.copy(options = trigger.options.toMutableList().also { it[i] = n })) }, onRemove = {
                        onChange(trigger.copy(options = trigger.options.toMutableList().also { it.removeAt(i) }))
                    })
                }
                if (trigger.options.size < 10) {
                    TextButton(onClick = { onChange(trigger.copy(options = trigger.options + CommandOption("option${trigger.options.size + 1}"))) }) {
                        Text(stringResource(R.string.f_add_option))
                    }
                }
            }
            is Trigger.PrefixCommand -> {
                TextInput(stringResource(R.string.f_command_name), trigger.name, { onChange(trigger.copy(name = it.trim().replace(" ", ""))) })
                Text(stringResource(R.string.f_prefix_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is Trigger.Message -> {
                ChoiceField(
                    stringResource(R.string.f_match_mode),
                    listOf(
                        MatchMode.CONTAINS to stringResource(R.string.m_contains),
                        MatchMode.STARTS_WITH to stringResource(R.string.m_starts_with),
                        MatchMode.EXACT to stringResource(R.string.m_exact),
                        MatchMode.REGEX to stringResource(R.string.m_regex),
                        MatchMode.ANY to stringResource(R.string.t_message_any),
                    ),
                    trigger.mode, { onChange(trigger.copy(mode = it)) },
                )
                if (trigger.mode != MatchMode.ANY) {
                    TextInput(stringResource(R.string.f_text), trigger.text, { onChange(trigger.copy(text = it)) })
                    SwitchRow(stringResource(R.string.f_ignore_case), trigger.ignoreCase) { onChange(trigger.copy(ignoreCase = it)) }
                }
            }
            is Trigger.ButtonClick -> {
                TextInput(stringResource(R.string.f_button_id), trigger.buttonId, { onChange(trigger.copy(buttonId = it.trim())) })
                Text(stringResource(R.string.f_button_id_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is Trigger.Interval -> {
                NumberInput(stringResource(R.string.f_minutes), trigger.minutes) { onChange(trigger.copy(minutes = it.coerceAtLeast(1))) }
                IdPicker(stringResource(R.string.f_channel), trigger.channelId, pickers.channels, stringResource(R.string.editor_start_bot_for_list)) { onChange(trigger.copy(channelId = it)) }
            }
            Trigger.MemberJoin, Trigger.MemberLeave -> Text(stringResource(R.string.f_members_intent_hint), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow)
        }
        if (trigger is Trigger.PrefixCommand || (trigger is Trigger.Message && trigger.mode != MatchMode.ANY)) {
            Text(stringResource(R.string.f_content_intent_hint), style = MaterialTheme.typography.bodySmall, color = PbColors.Yellow)
        }
    }
}

@Composable
private fun OptionEditor(option: CommandOption, onChange: (CommandOption) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            TextInput(stringResource(R.string.f_option_name), option.name, { onChange(option.copy(name = sanitizeCommandName(it))) })
        }
        TextInput(stringResource(R.string.f_command_description), option.description, { onChange(option.copy(description = it.take(100))) })
        ChoiceField(
            stringResource(R.string.f_option_type),
            listOf(
                OptionType.TEXT to stringResource(R.string.o_text),
                OptionType.NUMBER to stringResource(R.string.o_number),
                OptionType.USER to stringResource(R.string.o_user),
                OptionType.ROLE to stringResource(R.string.o_role),
                OptionType.CHANNEL to stringResource(R.string.o_channel),
                OptionType.YES_NO to stringResource(R.string.o_yes_no),
            ),
            option.type, { onChange(option.copy(type = it)) },
        )
        SwitchRow(stringResource(R.string.f_required), option.required) { onChange(option.copy(required = it)) }
        Text(stringResource(R.string.f_option_usage, option.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRemove) { Text(stringResource(R.string.editor_remove), color = PbColors.Red) }
    }
}

@Composable
fun ConditionFields(condition: Condition, pickers: PickerData, onChange: (Condition) -> Unit) {
    val noList = stringResource(R.string.editor_start_bot_for_list)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (condition) {
            is Condition.HasRole -> IdPicker(stringResource(R.string.f_role), condition.roleId, pickers.roles, noList) { onChange(condition.copy(roleId = it)) }
            is Condition.LacksRole -> IdPicker(stringResource(R.string.f_role), condition.roleId, pickers.roles, noList) { onChange(condition.copy(roleId = it)) }
            is Condition.InChannel -> IdPicker(stringResource(R.string.f_channel), condition.channelId, pickers.channels, noList) { onChange(condition.copy(channelId = it)) }
            is Condition.Chance -> NumberInput(stringResource(R.string.f_percent), condition.percent) { onChange(condition.copy(percent = it.coerceIn(0, 100))) }
            is Condition.Variable -> {
                ScopeChoice(condition.scope) { onChange(condition.copy(scope = it)) }
                TextInput(stringResource(R.string.f_var_name), condition.name, { onChange(condition.copy(name = it.trim())) })
                ChoiceField(
                    stringResource(R.string.f_compare),
                    listOf(CompareOp.EQ to "=", CompareOp.NE to "≠", CompareOp.LT to "<", CompareOp.LE to "≤", CompareOp.GT to ">", CompareOp.GE to "≥"),
                    condition.op, { onChange(condition.copy(op = it)) },
                )
                TextInput(stringResource(R.string.f_value), condition.value, { onChange(condition.copy(value = it)) })
            }
            Condition.IsAdmin, Condition.NotAdmin -> Unit
        }
    }
}

@Composable
private fun ScopeChoice(scope: VarScope, onChange: (VarScope) -> Unit) {
    ChoiceField(
        stringResource(R.string.f_scope),
        listOf(VarScope.USER to stringResource(R.string.s_user), VarScope.SERVER to stringResource(R.string.s_server), VarScope.GLOBAL to stringResource(R.string.s_global)),
        scope, onChange,
    )
}

@Composable
private fun TargetChoice(target: String, userOptions: List<String>, onChange: (String) -> Unit) {
    ChoiceField(
        stringResource(R.string.f_target),
        listOf(TARGET_USER to stringResource(R.string.f_target_user)) + userOptions.map { "arg:$it" to stringResource(R.string.f_target_option, it) },
        target, onChange,
    )
}

@Composable
fun ActionFields(action: Action, pickers: PickerData, userOptions: List<String>, onChange: (Action) -> Unit) {
    val noList = stringResource(R.string.editor_start_bot_for_list)
    val sameChannel = stringResource(R.string.f_channel_same)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (action) {
            is Action.Reply -> {
                TextInput(stringResource(R.string.f_text), action.text, { onChange(action.copy(text = it)) }, multiline = true, placeholders = true)
                SwitchRow(stringResource(R.string.f_private), action.private) { onChange(action.copy(private = it)) }
            }
            is Action.Send -> {
                IdPicker(stringResource(R.string.f_channel) + " " + sameChannel, action.channelId, pickers.channels, noList) { onChange(action.copy(channelId = it)) }
                TextInput(stringResource(R.string.f_text), action.text, { onChange(action.copy(text = it)) }, multiline = true, placeholders = true)
            }
            is Action.Dm -> {
                TargetChoice(action.target, userOptions) { onChange(action.copy(target = it)) }
                TextInput(stringResource(R.string.f_text), action.text, { onChange(action.copy(text = it)) }, multiline = true, placeholders = true)
            }
            is Action.Embed -> {
                TextInput(stringResource(R.string.f_title), action.title, { onChange(action.copy(title = it)) }, placeholders = true)
                TextInput(stringResource(R.string.f_text), action.description, { onChange(action.copy(description = it)) }, multiline = true, placeholders = true)
                TextInput(stringResource(R.string.f_color), action.color, { onChange(action.copy(color = it.take(7))) })
                IdPicker(stringResource(R.string.f_channel) + " " + sameChannel, action.channelId, pickers.channels, noList) { onChange(action.copy(channelId = it)) }
            }
            is Action.ButtonReply -> {
                TextInput(stringResource(R.string.f_text), action.text, { onChange(action.copy(text = it)) }, multiline = true, placeholders = true)
                TextInput(stringResource(R.string.f_button_label), action.label, { onChange(action.copy(label = it)) })
                TextInput(stringResource(R.string.f_button_id), action.buttonId, { onChange(action.copy(buttonId = it.trim())) })
                Text(stringResource(R.string.f_button_reply_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is Action.AddRole -> {
                IdPicker(stringResource(R.string.f_role), action.roleId, pickers.roles, noList) { onChange(action.copy(roleId = it)) }
                TargetChoice(action.target, userOptions) { onChange(action.copy(target = it)) }
            }
            is Action.RemoveRole -> {
                IdPicker(stringResource(R.string.f_role), action.roleId, pickers.roles, noList) { onChange(action.copy(roleId = it)) }
                TargetChoice(action.target, userOptions) { onChange(action.copy(target = it)) }
            }
            is Action.Timeout -> {
                TargetChoice(action.target, userOptions) { onChange(action.copy(target = it)) }
                TextInput(stringResource(R.string.f_minutes), action.minutes, { onChange(action.copy(minutes = it)) })
                TextInput(stringResource(R.string.f_reason), action.reason, { onChange(action.copy(reason = it)) })
            }
            is Action.Kick -> {
                TargetChoice(action.target, userOptions) { onChange(action.copy(target = it)) }
                TextInput(stringResource(R.string.f_reason), action.reason, { onChange(action.copy(reason = it)) })
            }
            is Action.Ban -> {
                TargetChoice(action.target, userOptions) { onChange(action.copy(target = it)) }
                TextInput(stringResource(R.string.f_reason), action.reason, { onChange(action.copy(reason = it)) })
            }
            is Action.React -> TextInput(stringResource(R.string.f_emoji), action.emoji, { onChange(action.copy(emoji = it.trim())) })
            is Action.VarAdd -> {
                ScopeChoice(action.scope) { onChange(action.copy(scope = it)) }
                TextInput(stringResource(R.string.f_var_name), action.name, { onChange(action.copy(name = it.trim())) })
                TextInput(stringResource(R.string.f_amount), action.amount, { onChange(action.copy(amount = it)) })
            }
            is Action.VarSet -> {
                ScopeChoice(action.scope) { onChange(action.copy(scope = it)) }
                TextInput(stringResource(R.string.f_var_name), action.name, { onChange(action.copy(name = it.trim())) })
                TextInput(stringResource(R.string.f_value), action.value, { onChange(action.copy(value = it)) })
            }
            is Action.Wait -> NumberInput(stringResource(R.string.f_seconds), action.seconds) { onChange(action.copy(seconds = it.coerceIn(0, 300))) }
            is Action.NotifyMe -> TextInput(stringResource(R.string.f_text), action.text, { onChange(action.copy(text = it)) }, multiline = true, placeholders = true)
            Action.DeleteMessage -> Text(stringResource(R.string.f_message_only_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action is Action.React) {
            Text(stringResource(R.string.f_message_only_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
