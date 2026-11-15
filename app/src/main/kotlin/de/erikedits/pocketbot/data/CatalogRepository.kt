package de.erikedits.pocketbot.data

import android.content.Context
import de.erikedits.pocketbot.shared.catalog.Catalog
import de.erikedits.pocketbot.shared.catalog.CatalogClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bot-type catalog (SPEC-v2 2): bundled copy in the APK, newer versions downloaded from GitHub
 * and cached in files/catalog/.
 */
class CatalogRepository(private val context: Context, private val client: CatalogClient) {
    private val cacheDir = File(context.filesDir, "catalog")
    private val _catalog = MutableStateFlow(loadBest())
    val catalog: StateFlow<Catalog> = _catalog.asStateFlow()

    private fun bundled(): Catalog = Catalog.load(context.assets.open("catalog/index.json").bufferedReader().use { it.readText() }) { path ->
        runCatching { context.assets.open("catalog/$path").bufferedReader().use { it.readText() } }.getOrNull()
    }

    private fun cached(): Catalog? = runCatching {
        val index = File(cacheDir, "index.json").takeIf { it.exists() } ?: return null
        Catalog.load(index.readText()) { File(cacheDir, it).takeIf(File::exists)?.readText() }
    }.getOrNull()

    private fun loadBest(): Catalog {
        val base = bundled()
        val cache = cached() ?: return base
        return if (cache.index.version >= base.index.version && cache.index.minFormat <= Catalog.FORMAT) {
            // Keep bundled types that the cached index somehow misses.
            cache.copy(types = base.types + cache.types)
        } else {
            base
        }
    }

    /** Checks GitHub for a newer catalog. Returns true if it changed. Failures are ignored (offline). */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val indexJson = client.fetchIndexJson()
            val index = Catalog.parseIndex(indexJson)
            if (index.minFormat > Catalog.FORMAT || index.version <= _catalog.value.index.version) return@runCatching false
            val files = index.types.associate { it.file to client.fetchFile(it.file) }
            val fresh = Catalog.load(indexJson) { files[it] }
            if (fresh.types.isEmpty()) return@runCatching false
            cacheDir.mkdirs()
            files.forEach { (path, text) -> File(cacheDir, path).apply { parentFile?.mkdirs() }.writeText(text) }
            File(cacheDir, "index.json").writeText(indexJson)
            _catalog.value = loadBest()
            true
        }.getOrDefault(false)
    }
}
