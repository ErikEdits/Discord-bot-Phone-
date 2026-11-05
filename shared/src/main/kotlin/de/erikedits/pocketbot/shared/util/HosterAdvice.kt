package de.erikedits.pocketbot.shared.util

import de.erikedits.pocketbot.shared.api.RecommendThreshold

/** Decides when the app suggests a real hoster instead of the phone (SPEC 2.3 step 7). */
object HosterAdvice {
    fun shouldRecommend(
        multipleServersPlanned: Boolean,
        guilds: Int,
        members: Int,
        threshold: RecommendThreshold = RecommendThreshold(),
    ): Boolean = multipleServersPlanned || guilds >= threshold.guilds || members >= threshold.members
}
