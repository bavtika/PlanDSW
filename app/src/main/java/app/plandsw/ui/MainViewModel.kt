package app.plandsw.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.plandsw.BuildConfig
import app.plandsw.R
import app.plandsw.data.CachedSchedule
import app.plandsw.data.Lesson
import app.plandsw.data.Option
import app.plandsw.data.Repository
import app.plandsw.data.ScheduleChange
import app.plandsw.data.Target
import app.plandsw.data.TargetKind
import app.plandsw.data.TokFilters
import app.plandsw.data.fold
import app.plandsw.update.ApkInstaller
import app.plandsw.update.AppUpdate
import app.plandsw.update.UpdateChecker
import app.plandsw.widget.PlanWidget
import app.plandsw.widget.RefreshWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** A tok group: full name from the site and a short label for cards ("Ćw1S"). */
data class GroupChoice(val name: String, val short: String)

data class ScheduleState(
    val target: Target? = null,
    val lessonsByDate: Map<LocalDate, List<Lesson>> = emptyMap(),
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    /** Tok only: all of its groups and the ones the user selected. */
    val groups: List<GroupChoice> = emptyList(),
    val selectedGroups: Set<String> = emptySet(),
    /** Tok opened for the first time - prompt for group selection right away. */
    val askGroups: Boolean = false,
)

data class SearchState(
    val kind: TargetKind = TargetKind.TEACHER,
    val query: String = "",
    val results: List<Target> = emptyList(),
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val searched: Boolean = false,
    /** How many leading results are teachers from your schedule (a separate section on top). */
    val mine: Int = 0,
)

data class UpdateState(
    val available: AppUpdate? = null,
    val checking: Boolean = false,
    /** Downloaded fraction 0..1 while the APK is downloading. */
    val progress: Float? = null,
    val downloaded: File? = null,
    @StringRes val message: Int? = null,
    val bannerHidden: Boolean = false,
)

enum class SetupStep { FACULTY, KIERUNEK, INTAKE, MODE, TOK }

data class SetupState(
    val step: SetupStep = SetupStep.FACULTY,
    val filters: TokFilters? = null,
    val kierunki: List<Option> = emptyList(),
    val faculty: Option? = null,
    val kierunek: Option? = null,
    val intake: Option? = null,
    val mode: Option? = null,
    val toks: List<Target> = emptyList(),
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    /** Selection finished - the wizard screen closes and opens this tok. */
    val done: Target? = null,
) {
    val options: List<Option>
        get() = when (step) {
            SetupStep.FACULTY -> filters?.faculties.orEmpty()
            SetupStep.KIERUNEK -> kierunki
            SetupStep.INTAKE -> filters?.intakes.orEmpty()
            SetupStep.MODE -> filters?.modes.orEmpty()
            SetupStep.TOK -> toks.map { Option(it.id.toString(), it.name) }
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)

    val favorites: StateFlow<List<Target>> = repo.favorites

    private val _schedule = MutableStateFlow(ScheduleState())
    val schedule: StateFlow<ScheduleState> = _schedule.asStateFlow()

    /** Changes are shown only while the main schedule is on screen. */
    val changes: StateFlow<List<ScheduleChange>> = combine(repo.changes, _schedule) { list, state ->
        if (state.target != null && state.target.key == repo.primary?.key) list else emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearChanges() = repo.clearChanges()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    /** The last loaded schedule without the group filter. */
    private var current: CachedSchedule? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var setupJob: Job? = null
    private var rooms: List<Target>? = null
    // The list is downloaded separately from search: typing during the download must not restart it.
    private var teachersLoad: Deferred<List<Target>>? = null

    val hasSchedule: Boolean get() = _schedule.value.target != null

    val languageChosen: Boolean get() = repo.languageTag != null

    /** Language saved in the app; null - not chosen yet. */
    val savedLanguage: String? get() = repo.languageTag

    fun chooseLanguage(tag: String) {
        repo.languageTag = tag
    }

    private val _theme = MutableStateFlow(repo.theme)
    val theme: StateFlow<String> = _theme.asStateFlow()

    fun setTheme(mode: String) {
        repo.theme = mode
        _theme.value = mode
    }

    // Declared before init: checkForUpdate() is already called there.
    private val _update = MutableStateFlow(UpdateState())
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    private val updateChecker = UpdateChecker()
    private val installer = ApkInstaller(app)

    init {
        (repo.lastOpened ?: favorites.value.firstOrNull())?.let { open(it) }
        viewModelScope.launch {
            // KEEP: if the work is already scheduled, nothing changes.
            if (GlanceAppWidgetManager(app).getGlanceIds(PlanWidget::class.java).isNotEmpty()) RefreshWorker.schedule(app)
        }
        checkForUpdate(manual = false)
    }

    fun toggleFavorite(t: Target) = repo.toggleFavorite(t)

    // ---- App updates ----

    fun checkForUpdate(manual: Boolean) {
        if (_update.value.checking) return
        if (!manual && System.currentTimeMillis() - repo.lastUpdateCheck < DAY_MS) return
        viewModelScope.launch {
            _update.update { it.copy(checking = true, message = null) }
            try {
                val found = updateChecker.availableUpdate(BuildConfig.VERSION_NAME)
                // Until a found update is installed, check on every launch so the banner does not disappear.
                if (found == null) repo.lastUpdateCheck = System.currentTimeMillis()
                _update.update {
                    it.copy(
                        checking = false,
                        available = found,
                        bannerHidden = false,
                        message = if (manual && found == null) R.string.update_latest else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.update { it.copy(checking = false, message = if (manual) R.string.update_failed else null) }
            }
        }
    }

    fun installUpdate() {
        val state = _update.value
        val update = state.available ?: return
        if (state.progress != null) return
        state.downloaded?.takeIf { it.exists() }?.let { file ->
            if (!installer.install(file)) _update.update { it.copy(message = R.string.update_allow_install) }
            return
        }
        viewModelScope.launch {
            _update.update { it.copy(progress = 0f, message = null) }
            try {
                val file = installer.download(update) { p -> _update.update { it.copy(progress = p) } }
                _update.update { it.copy(progress = null, downloaded = file) }
                if (!installer.install(file)) _update.update { it.copy(message = R.string.update_allow_install) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.update { it.copy(progress = null, message = R.string.update_download_failed) }
            }
        }
    }

    fun hideUpdateBanner() = _update.update { it.copy(bannerHidden = true) }

    fun open(target: Target) {
        repo.lastOpened = target
        // Set the target right away: on startup the screen uses it to decide whether to show the wizard.
        if (_schedule.value.target?.key != target.key) {
            current = null
            _schedule.value = ScheduleState(target = target, loading = true)
        }
        load(target, force = false)
    }

    fun refresh() {
        _schedule.value.target?.let { load(it, force = true) }
    }

    private fun load(target: Target, force: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val onScreen = _schedule.value.target?.key == target.key && _schedule.value.fetchedAt != null
            val cached = if (onScreen) null else repo.cached(target)
            if (cached != null) {
                show(cached)
            } else if (!onScreen) {
                current = null
                _schedule.value = ScheduleState(target = target)
            }
            val fetchedAt = _schedule.value.fetchedAt
            if (!force && fetchedAt != null && System.currentTimeMillis() - fetchedAt < STALE_AFTER_MS) return@launch

            _schedule.update { it.copy(loading = true, error = null) }
            try {
                show(repo.refresh(target))
                if (target.key == repo.primary?.key) PlanWidget().updateAll(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _schedule.update { it.copy(loading = false, error = e.toErrorRes()) }
            }
        }
    }

    // ---- Tok groups ----

    fun setGroups(selected: Set<String>) {
        val target = _schedule.value.target ?: return
        repo.setSelectedGroups(target, selected)
        current?.let(::show)
        if (target.key == repo.primary?.key) viewModelScope.launch { PlanWidget().updateAll(getApplication()) }
    }

    fun dismissGroupPrompt() {
        val state = _schedule.value
        val target = state.target ?: return
        // Closed without choosing - remember "all groups" so we don't ask again.
        if (repo.selectedGroups(target) == null) repo.setSelectedGroups(target, state.groups.map { it.name }.toSet())
        _schedule.update { it.copy(askGroups = false) }
    }

    private fun show(cached: CachedSchedule) {
        current = cached
        val target = cached.target
        var lessons = cached.lessons
        var groups = emptyList<GroupChoice>()
        var selected = emptySet<String>()
        var ask = false
        if (target.kind == TargetKind.TOK) {
            val names = lessons.flatMap { it.groupNames }.distinct()
            groups = shortGroupNames(names).sortedWith(compareBy({ groupCategory(it.short) }, { it.short }))
            val saved = repo.selectedGroups(target)?.filter { it in names }?.toSet()
            ask = saved == null && groups.size > 1
            // No groups chosen yet: on first display preselect only lectures, they are shared by everyone.
            val lectures = groups.filter { groupCategory(it.short) == GroupCategory.LECTURE }.map { it.name }.toSet()
            selected = saved?.takeIf { it.isNotEmpty() }
                ?: if (ask && lectures.isNotEmpty()) lectures else names.toSet()
            val shortByName = groups.associate { it.name to it.short }
            lessons = lessons
                .filter { l -> l.groupNames.isEmpty() || l.groupNames.any { it in selected } }
                .map { l ->
                    val codes = l.groupNames.map { shortByName[it] ?: it }
                    // A group label adds nothing on lectures - everyone attends them.
                    val label = if (codes.all { groupCategory(it) == GroupCategory.LECTURE }) ""
                    else codes.joinToString(", ") { groupCode(it) }
                    l.copy(groups = label)
                }
        }
        _schedule.value = ScheduleState(
            target = target,
            lessonsByDate = lessons.groupBy { it.date },
            from = LocalDate.parse(cached.from),
            to = LocalDate.parse(cached.to),
            fetchedAt = cached.fetchedAt,
            groups = groups,
            selectedGroups = selected,
            askGroups = ask,
        )
    }

    // ---- Tok selection wizard ----

    fun startSetup() {
        setupJob?.cancel()
        _setup.value = SetupState(filters = _setup.value.filters)
        if (_setup.value.filters == null) loadFilters()
    }

    private fun loadFilters() = setupTask {
        val filters = repo.tokFilters()
        if (filters.faculties.isEmpty()) throw UserFacingException(R.string.error_no_faculties)
        _setup.update { it.copy(filters = filters) }
    }

    fun setupRetry() {
        val s = _setup.value
        when {
            s.filters == null -> loadFilters()
            s.step == SetupStep.KIERUNEK && s.faculty != null -> loadKierunki(s.faculty)
            s.step == SetupStep.MODE && s.mode != null -> searchToks()
        }
    }

    fun setupPick(option: Option) {
        val s = _setup.value
        if (s.loading) return
        when (s.step) {
            SetupStep.FACULTY -> {
                _setup.update { it.copy(faculty = option, step = SetupStep.KIERUNEK, kierunki = emptyList()) }
                loadKierunki(option)
            }
            SetupStep.KIERUNEK -> _setup.update { it.copy(kierunek = option, step = SetupStep.INTAKE) }
            SetupStep.INTAKE -> _setup.update { it.copy(intake = option, step = SetupStep.MODE) }
            SetupStep.MODE -> {
                _setup.update { it.copy(mode = option) }
                searchToks()
            }
            SetupStep.TOK -> s.toks.firstOrNull { it.id.toString() == option.id }?.let(::finishSetup)
        }
    }

    /** @return false if we are already on the first step. */
    fun setupBack(): Boolean {
        setupJob?.cancel()
        val s = _setup.value
        val prev = when (s.step) {
            SetupStep.FACULTY -> return false
            SetupStep.KIERUNEK -> SetupStep.FACULTY
            SetupStep.INTAKE -> SetupStep.KIERUNEK
            SetupStep.MODE -> SetupStep.INTAKE
            SetupStep.TOK -> SetupStep.MODE
        }
        _setup.update { it.copy(step = prev, loading = false, error = null) }
        return true
    }

    fun setupConsumed() = _setup.update { it.copy(done = null) }

    private fun loadKierunki(faculty: Option) = setupTask {
        val list = repo.kierunki(faculty.id)
        if (list.isEmpty()) throw UserFacingException(R.string.error_no_kierunki)
        _setup.update { it.copy(kierunki = list) }
    }

    private fun searchToks() = setupTask {
        val s = _setup.value
        val toks = repo.searchToks(s.faculty!!.id, s.kierunek!!.id, s.intake!!.id, s.mode!!.id)
        when (toks.size) {
            0 -> throw UserFacingException(R.string.error_no_toks)
            1 -> finishSetup(toks.single())
            else -> _setup.update { it.copy(toks = toks, step = SetupStep.TOK) }
        }
    }

    private fun finishSetup(tok: Target) {
        repo.primary = tok
        repo.addFavorite(tok)
        open(tok)
        _setup.update { it.copy(done = tok) }
    }

    private fun setupTask(block: suspend () -> Unit) {
        setupJob?.cancel()
        setupJob = viewModelScope.launch {
            _setup.update { it.copy(loading = true, error = null) }
            try {
                block()
                _setup.update { it.copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _setup.update { it.copy(loading = false, error = e.toErrorRes()) }
            }
        }
    }

    // ---- Teacher / room search ----

    fun setSearchKind(kind: TargetKind) {
        if (kind == _search.value.kind) return
        _search.value = SearchState(kind = kind, query = _search.value.query)
        runSearch(debounce = false)
    }

    fun setQuery(q: String) {
        _search.update { it.copy(query = q) }
        runSearch(debounce = true)
    }

    fun submitSearch() = runSearch(debounce = false)

    /** Search screen opened - show the full list right away. */
    fun openSearch() = runSearch(debounce = false)

    private fun runSearch(debounce: Boolean) {
        searchJob?.cancel()
        val (kind, query) = _search.value.let { it.kind to it.query.trim() }
        searchJob = viewModelScope.launch {
            val loaded = if (kind == TargetKind.ROOM) rooms != null else teachersLoad?.isCompleted == true
            if (!loaded) _search.update { it.copy(loading = true, error = null) }
            try {
                var mine = 0
                val results = when (kind) {
                    TargetKind.ROOM -> filterTargets(rooms ?: repo.rooms().also { rooms = it }, query)
                    else -> {
                        val all = allTeachers()
                        if (all.isEmpty()) {
                            // The site did not return the full list - search by surname as before.
                            if (debounce) delay(500)
                            if (query.length < 2) emptyList() else repo.searchTeachers(query)
                        } else {
                            val own = myTeachers()
                            val (first, rest) = filterTargets(all, query).partition { it.name.fold() in own }
                            mine = first.size
                            first + rest
                        }
                    }
                }
                _search.update { it.copy(results = results, mine = mine, loading = false, error = null, searched = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.update { it.copy(loading = false, error = e.toErrorRes()) }
            }
        }
    }

    private fun filterTargets(all: List<Target>, query: String): List<Target> {
        val words = query.fold().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return all.filter { t ->
            val haystack = "${t.name} ${t.subtitle}".fold()
            words.all { it in haystack }
        }
    }

    private suspend fun allTeachers(): List<Target> {
        val load = teachersLoad?.takeUnless { it.isCompleted && it.getCompletionExceptionOrNull() != null }
            ?: viewModelScope.async { repo.teachers() }.also { teachersLoad = it }
        return load.await()
    }

    /** Teachers from the main schedule (names without diacritics) - shown first. */
    private suspend fun myTeachers(): Set<String> =
        repo.primaryLessons().orEmpty()
            .flatMap { it.teacher.split(',', ';') }
            .map { it.trim().fold() }
            .filter { it.isNotEmpty() }
            .toSet()

    companion object {
        private const val STALE_AFTER_MS = 60 * 60 * 1000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
