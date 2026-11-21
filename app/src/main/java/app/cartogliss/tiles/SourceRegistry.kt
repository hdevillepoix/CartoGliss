package app.cartogliss.tiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/** User-side catalog state, stored as JSON in app storage. */
@Serializable
data class UserSourcesFile(
    val custom: List<SourceDto> = emptyList(),
    /** Built-in id → user-raised (or lowered) max zoom, e.g. OS premium levels. */
    val maxZoomOverrides: Map<String, Int> = emptyMap(),
    val hidden: Set<String> = emptySet(),
)

/**
 * All tile sources: the bundled catalog plus the user's custom sources, with overrides applied.
 * Ids are stable: stars, overrides and the cache are keyed by them.
 */
class SourceRegistry(
    private val builtIns: List<TileSource>,
    private val file: File,
) {
    private val lock = Any()
    private var user: UserSourcesFile = load()

    private val _sources = MutableStateFlow(compose())

    /** Visible and hidden sources alike; filter with [isHidden]. Built-ins first, in catalog order. */
    val sources: StateFlow<List<TileSource>> = _sources

    fun byId(id: String): TileSource? = _sources.value.firstOrNull { it.id == id }
    fun resolve(id: String): TileSource = byId(id) ?: byId(BuiltInSources.DEFAULT_ID) ?: BuiltInSources.OSM_STANDARD

    fun isHidden(id: String) = id in user.hidden
    fun maxZoomOverride(id: String): Int? = user.maxZoomOverrides[id]
    fun builtInDefault(id: String): TileSource? = builtIns.firstOrNull { it.id == id }

    /** Categories in display order: General, Hiking, Biking, Local, then any custom ones alphabetically. */
    fun categories(): List<String> {
        val known = listOf("General", "Hiking", "Biking", "Local")
        val all = _sources.value.map { it.category }.toSet()
        return known.filter { it in all } + (all - known.toSet()).sorted()
    }

    fun newCustomId(): String = "custom-" + UUID.randomUUID().toString().take(8)

    /** Add or replace a custom source. */
    fun saveCustom(s: TileSource) = edit { u ->
        val dto = SourceDto.from(s.copy(builtIn = false))
        u.copy(custom = u.custom.filter { it.id != s.id } + dto)
    }

    fun deleteCustom(id: String) = edit { u -> u.copy(custom = u.custom.filter { it.id != id }, hidden = u.hidden - id) }

    fun setMaxZoomOverride(id: String, maxZoom: Int?) = edit { u ->
        u.copy(maxZoomOverrides = if (maxZoom == null) u.maxZoomOverrides - id else u.maxZoomOverrides + (id to maxZoom))
    }

    fun setHidden(id: String, hidden: Boolean) = edit { u -> u.copy(hidden = if (hidden) u.hidden + id else u.hidden - id) }

    private fun edit(f: (UserSourcesFile) -> UserSourcesFile) {
        synchronized(lock) {
            user = f(user)
            save(user)
            _sources.value = compose()
        }
    }

    private fun compose(): List<TileSource> {
        val custom = user.custom.mapNotNull { runCatching { it.toSource(builtIn = false) }.getOrNull() }
        val builtInIds = builtIns.map { it.id }.toSet()
        return builtIns.map { b -> user.maxZoomOverrides[b.id]?.let { b.copy(maxZoom = it) } ?: b } +
            custom.filter { it.id !in builtInIds }
    }

    private fun load(): UserSourcesFile =
        if (!file.exists()) UserSourcesFile()
        else runCatching { SourceCatalog.json.decodeFromString<UserSourcesFile>(file.readText()) }.getOrDefault(UserSourcesFile())

    private fun save(u: UserSourcesFile) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(SourceCatalog.json.encodeToString(UserSourcesFile.serializer(), u))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
