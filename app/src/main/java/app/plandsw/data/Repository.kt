package app.plandsw.data

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Favourite schedules and the offline cache. */
class Repository private constructor(context: Context, private val api: IdeisApi) {
    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext, IdeisApi()).also { instance = it }
        }

        private const val MAX_CHANGES = 50
    }

    private val prefs = context.getSharedPreferences("plan", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "schedules").apply { mkdirs() }
    private val roomsFile = File(context.filesDir, "rooms.json")
    private val teachersFile = File(context.filesDir, "teachers.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val changesFile = File(context.filesDir, "changes.json")
    private val _changes = MutableStateFlow(readChanges())

    /** Unseen changes to the primary schedule, newest first. */
    val changes: StateFlow<List<ScheduleChange>> = _changes.asStateFlow()

    @Synchronized
    private fun addChanges(found: List<ScheduleChange>) {
        if (found.isEmpty()) return
        _changes.value = (found + _changes.value).take(MAX_CHANGES)
        changesFile.writeText(json.encodeToString(_changes.value))
    }

    @Synchronized
    fun clearChanges() {
        _changes.value = emptyList()
        changesFile.delete()
    }

    private fun readChanges(): List<ScheduleChange> =
        if (!changesFile.exists()) emptyList()
        else runCatching { json.decodeFromString<List<ScheduleChange>>(changesFile.readText()) }.getOrDefault(emptyList())

    private val _favorites = MutableStateFlow(readFavorites())
    val favorites: StateFlow<List<Target>> = _favorites.asStateFlow()

    fun isFavorite(t: Target) = _favorites.value.any { it.key == t.key }

    fun toggleFavorite(t: Target) {
        val list = _favorites.value
        _favorites.value = if (isFavorite(t)) list.filterNot { it.key == t.key } else list + t
        prefs.edit().putString("favorites", json.encodeToString(_favorites.value)).apply()
    }

    var lastOpened: Target?
        get() = prefs.getString("last", null)?.let { runCatching { json.decodeFromString<Target>(it) }.getOrNull() }
        set(value) {
            prefs.edit().putString("last", value?.let { json.encodeToString(it) }).apply()
        }

    private val _widgetVersion = MutableStateFlow(0L)

    /** Bumped whenever what the widget shows may have changed: it re-reads data right inside the live session. */
    val widgetVersion: StateFlow<Long> = _widgetVersion.asStateFlow()

    private fun bumpWidget() = _widgetVersion.update { it + 1 }

    /**
     * The primary schedule is the tok chosen in the wizard. The widget and change detection work with it.
     * The 1.0.x wizard did not save it: then take the last opened tok, otherwise the first tok in favourites
     * (the old wizard added the tok there), and remember it right away — otherwise after opening a teacher
     * or a room the widget loses its schedule.
     */
    var primary: Target?
        get() {
            prefs.getString("primary", null)
                ?.let { runCatching { json.decodeFromString<Target>(it) }.getOrNull() }
                ?.let { return it }
            val fallback = lastOpened?.takeIf { it.kind == TargetKind.TOK }
                ?: _favorites.value.firstOrNull { it.kind == TargetKind.TOK }
                ?: return null
            // Write directly, without bumpWidget(): the widget reads primary while loading data, otherwise it would loop.
            prefs.edit().putString("primary", json.encodeToString(fallback)).apply()
            return fallback
        }
        set(value) {
            prefs.edit().putString("primary", value?.let { json.encodeToString(it) }).apply()
            bumpWidget()
        }

    /** Language chosen in the app (en/pl/ru/uk); null — not chosen yet. */
    var languageTag: String?
        get() = prefs.getString("language", null)
        set(value) {
            prefs.edit().putString("language", value).apply()
        }

    /** "system", "light" or "dark". */
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) {
            prefs.edit().putString("theme", value).apply()
        }

    /** When updates were last checked and nothing was found (ms). */
    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0L)
        set(value) {
            prefs.edit().putLong("lastUpdateCheck", value).apply()
        }

    suspend fun cached(t: Target): CachedSchedule? = withContext(Dispatchers.IO) {
        val f = File(cacheDir, "${t.key}.json")
        if (!f.exists()) null else runCatching { json.decodeFromString<CachedSchedule>(f.readText()) }.getOrNull()
    }

    suspend fun refresh(t: Target): CachedSchedule {
        val old = cached(t)
        val oldRange = old?.let { LocalDate.parse(it.from) to LocalDate.parse(it.to) }
        val data = api.fetchSchedule(t, oldRange)
        // Same semester, there were classes before and now there are none — a site failure, not a cancellation of everything.
        if (data.lessons.isEmpty() && !old?.lessons.isNullOrEmpty() && oldRange == (data.from to data.to)) {
            throw UnexpectedResponseException("Empty schedule for ${t.key} while cache has lessons")
        }
        val schedule = CachedSchedule(t, System.currentTimeMillis(), data.from.toString(), data.to.toString(), data.lessons)
        withContext(Dispatchers.IO) {
            File(cacheDir, "${t.key}.json").writeText(json.encodeToString(schedule))
        }
        if (t.key == primary?.key) {
            if (old != null) {
                withContext(Dispatchers.IO) {
                    addChanges(ScheduleDiff.diff(old.lessons, data.lessons, selectedGroups(t), LocalDate.now(WARSAW)))
                }
            }
            bumpWidget()
        }
        return schedule
    }

    /** Which tok groups to show; null — no selection made yet. */
    fun selectedGroups(t: Target): Set<String>? =
        prefs.getString("groups_${t.key}", null)
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?.toSet()

    fun setSelectedGroups(t: Target, groups: Set<String>) {
        prefs.edit().putString("groups_${t.key}", json.encodeToString(groups.toList())).apply()
        bumpWidget()
    }

    /** Primary schedule classes with the "my groups" filter; null — no schedule selected or loaded yet. */
    suspend fun primaryLessons(): List<Lesson>? {
        val p = primary ?: return null
        val cache = cached(p) ?: return null
        // The app also treats an empty selection as "all groups".
        val groups = selectedGroups(p)?.takeIf { it.isNotEmpty() }
        return cache.lessons.filter { l -> groups == null || l.groupNames.isEmpty() || l.groupNames.any { it in groups } }
    }

    /** Context in the app's language — the widget lives outside the activity and cannot learn the language on its own. */
    fun localizedContext(context: Context): Context {
        val tag = languageTag ?: return context
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return context.createConfigurationContext(config)
    }

    fun addFavorite(t: Target) {
        if (!isFavorite(t)) toggleFavorite(t)
    }

    suspend fun tokFilters() = api.loadTokFilters()

    suspend fun kierunki(facultyId: String) = api.loadKierunki(facultyId)

    suspend fun searchToks(faculty: String, kierunek: String, intake: String, mode: String) =
        api.searchToks(faculty, kierunek, intake, mode)

    suspend fun searchTeachers(q: String) = api.searchTeachers(q)

    suspend fun rooms(): List<Target> = weeklyList(roomsFile) { api.loadRooms() }

    suspend fun teachers(): List<Target> = weeklyList(teachersFile) { api.loadAllTeachers() }

    /** Room and teacher lists rarely change — keep them on disk for a week. */
    private suspend fun weeklyList(file: File, load: suspend () -> List<Target>): List<Target> {
        val cached = withContext(Dispatchers.IO) {
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < 7 * 24 * 3600_000L) {
                runCatching { json.decodeFromString<List<Target>>(file.readText()) }.getOrNull()
            } else null
        }
        if (!cached.isNullOrEmpty()) return cached
        val fresh = load()
        if (fresh.isNotEmpty()) withContext(Dispatchers.IO) { file.writeText(json.encodeToString(fresh)) }
        return fresh
    }

    private fun readFavorites(): List<Target> =
        prefs.getString("favorites", null)
            ?.let { runCatching { json.decodeFromString<List<Target>>(it) }.getOrNull() }
            .orEmpty()
}
