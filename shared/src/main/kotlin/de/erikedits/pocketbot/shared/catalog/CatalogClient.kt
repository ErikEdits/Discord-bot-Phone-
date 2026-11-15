package de.erikedits.pocketbot.shared.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A loaded catalog: index + all type definitions. */
data class Catalog(val index: CatalogIndex, val types: Map<String, BotType>) {
    fun type(id: String): BotType? = types[id]

    companion object {
        /** Highest catalog format this app understands. */
        const val FORMAT = 1

        fun parseIndex(json: String): CatalogIndex = catalogJson.decodeFromString(CatalogIndex.serializer(), json)
        fun parseType(json: String): BotType = catalogJson.decodeFromString(BotType.serializer(), json)

        /** Builds a catalog from file contents (used for the bundled copy and the GitHub download). */
        fun load(indexJson: String, readFile: (String) -> String?): Catalog {
            val index = parseIndex(indexJson)
            val types = index.types.mapNotNull { e ->
                runCatching { readFile(e.file)?.let(::parseType) }.getOrNull()?.takeIf { it.id == e.id }?.let { e.id to it }
            }.toMap()
            return Catalog(index, types)
        }
    }
}

/** Downloads the catalog from GitHub (raw files). */
class CatalogClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://raw.githubusercontent.com/ErikEdits/Discord-bot-Phone-/main/catalog/",
) {
    suspend fun fetchIndexJson(): String = get("index.json")
    suspend fun fetchFile(path: String): String = get(path)

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path.trimStart('/')).header("Cache-Control", "no-cache").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $path")
            r.body?.string() ?: throw IOException("empty $path")
        }
    }
}
