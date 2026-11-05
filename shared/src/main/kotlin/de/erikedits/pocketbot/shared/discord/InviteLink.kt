package de.erikedits.pocketbot.shared.discord

/** Discord permission bits used by PocketBot (https://discord.com/developers/docs/topics/permissions). */
object Permission {
    const val KICK_MEMBERS = 1L shl 1
    const val BAN_MEMBERS = 1L shl 2
    const val MANAGE_CHANNELS = 1L shl 4
    const val ADD_REACTIONS = 1L shl 6
    const val VIEW_CHANNEL = 1L shl 10
    const val SEND_MESSAGES = 1L shl 11
    const val MANAGE_MESSAGES = 1L shl 13
    const val EMBED_LINKS = 1L shl 14
    const val ATTACH_FILES = 1L shl 15
    const val READ_MESSAGE_HISTORY = 1L shl 16
    const val USE_EXTERNAL_EMOJIS = 1L shl 18
    const val MANAGE_ROLES = 1L shl 28
    const val MODERATE_MEMBERS = 1L shl 40

    /** What every PocketBot needs to answer commands. */
    const val BASIC = VIEW_CHANNEL or SEND_MESSAGES or EMBED_LINKS or ATTACH_FILES or
        READ_MESSAGE_HISTORY or ADD_REACTIONS or USE_EXTERNAL_EMOJIS
}

object InviteLink {
    fun build(applicationId: String, permissions: Long = Permission.BASIC): String =
        "https://discord.com/oauth2/authorize?client_id=$applicationId" +
            "&scope=bot+applications.commands&permissions=$permissions"
}
