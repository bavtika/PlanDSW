package app.plandsw.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.plandsw.data.GradeChange
import app.plandsw.data.GradesRepository
import app.plandsw.data.TermGrades
import app.plandsw.data.UsosLoginRequired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GradesState(
    val loggedIn: Boolean = false,
    val terms: List<TermGrades> = emptyList(),
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    /** USOSweb session expired: show the cache and offer to log in again. */
    val loginRequired: Boolean = false,
    /** gradeKeys of grades highlighted as new while the grades screen is open. */
    val highlighted: Set<String> = emptySet(),
    /** Unseen new grades, shown as a banner on the main screen. */
    val unseen: List<GradeChange> = emptyList(),
)

class GradesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GradesRepository(app)

    private val _state = MutableStateFlow(GradesState(loggedIn = repo.loggedIn, unseen = repo.unseen))
    val state: StateFlow<GradesState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repo.cached()?.let { c -> _state.update { it.copy(terms = c.terms, fetchedAt = c.fetchedAt) } }
            // Quiet check for new grades on startup, for the banner on the main screen.
            if (repo.loggedIn && stale()) load(quiet = true)
        }
    }

    /** The grades screen was opened. */
    fun onOpen() {
        markSeen()
        if (repo.loggedIn && stale() && !_state.value.loading) load(quiet = false)
    }

    /** New grades are on screen: highlight them and hide the banner. */
    fun markSeen() {
        val keys = repo.unseen.map { it.key }
        repo.unseen = emptyList()
        _state.update { it.copy(highlighted = it.highlighted + keys, unseen = emptyList()) }
    }

    fun clearHighlight() = _state.update { it.copy(highlighted = emptySet()) }

    fun refresh() = load(quiet = false)

    fun onLoggedIn() {
        repo.loggedIn = true
        _state.update { it.copy(loggedIn = true, loginRequired = false, error = null) }
        load(quiet = false)
    }

    fun logout() {
        loadJob?.cancel()
        repo.logout()
        _state.value = GradesState()
    }

    private fun stale(): Boolean =
        _state.value.fetchedAt?.let { System.currentTimeMillis() - it > STALE_AFTER_MS } ?: true

    private fun load(quiet: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = !quiet, error = null) }
            try {
                val fresh = repo.refresh()
                _state.update {
                    it.copy(terms = fresh.terms, fetchedAt = fresh.fetchedAt, loading = false, loginRequired = false, unseen = repo.unseen)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UsosLoginRequired) {
                _state.update { it.copy(loading = false, loginRequired = true) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = if (quiet) null else e.toErrorRes()) }
            }
        }
    }

    companion object {
        private const val STALE_AFTER_MS = 60 * 60 * 1000L
    }
}
