package app.plandsw

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plandsw.ui.GradesScreen
import app.plandsw.ui.GradesViewModel
import app.plandsw.ui.LanguageScreen
import app.plandsw.ui.MainTab
import app.plandsw.ui.MainTabsBar
import app.plandsw.ui.MainViewModel
import app.plandsw.ui.PlanTheme
import app.plandsw.ui.ScheduleScreen
import app.plandsw.ui.SearchScreen
import app.plandsw.ui.SettingsScreen
import app.plandsw.ui.SetupScreen
import app.plandsw.ui.UsosLoginScreen
import app.plandsw.ui.applyLanguage
import app.plandsw.ui.isDarkTheme

private enum class Screen { LANGUAGE, SCHEDULE, SEARCH, SETUP, USOS_LOGIN }

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private val grades: GradesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After an update AppCompat may lose the app language, so restore it from our own saved choice.
        val savedTag = vm.savedLanguage
        if (AppCompatDelegate.getApplicationLocales().isEmpty && savedTag != null) applyLanguage(savedTag)
        enableEdgeToEdge()
        setContent {
            val themeMode by vm.theme.collectAsStateWithLifecycle()
            val dark = isDarkTheme(themeMode)
            LaunchedEffect(dark) {
                // Status bar icon color must follow the app theme, not the system one.
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PlanTheme(dark) {
                val schedule by vm.schedule.collectAsStateWithLifecycle()
                val search by vm.search.collectAsStateWithLifecycle()
                val setup by vm.setup.collectAsStateWithLifecycle()
                val favorites by vm.favorites.collectAsStateWithLifecycle()
                val changes by vm.changes.collectAsStateWithLifecycle()
                val update by vm.update.collectAsStateWithLifecycle()
                val gradesState by grades.state.collectAsStateWithLifecycle()
                // First launch: language -> program selection wizard.
                var screen by rememberSaveable {
                    mutableStateOf(
                        when {
                            !vm.languageChosen -> Screen.LANGUAGE
                            vm.hasSchedule -> Screen.SCHEDULE
                            else -> Screen.SETUP
                        }
                    )
                }
                var tab by rememberSaveable { mutableStateOf(MainTab.SCHEDULE) }
                // Keeps the day and the open subject while viewing grades, settings or search.
                val scheduleState = rememberSaveableStateHolder()
                LaunchedEffect(screen) {
                    if (screen == Screen.SETUP && setup.done == null && setup.filters == null) vm.startSetup()
                }
                LaunchedEffect(setup.done) {
                    if (setup.done != null) {
                        screen = Screen.SCHEDULE
                        tab = MainTab.SCHEDULE
                        vm.setupConsumed()
                    }
                }

                when (screen) {
                    Screen.LANGUAGE -> LanguageScreen(onChosen = { tag ->
                        vm.chooseLanguage(tag)
                        // Switch the screen before changing the language: the activity is recreated and restores the new screen.
                        screen = if (vm.hasSchedule) Screen.SCHEDULE else Screen.SETUP
                        applyLanguage(tag)
                    })
                    Screen.SETUP -> {
                        BackHandler {
                            if (!vm.setupBack()) {
                                if (vm.hasSchedule) screen = Screen.SCHEDULE else finish()
                            }
                        }
                        SetupScreen(
                            state = setup,
                            canClose = vm.hasSchedule,
                            onPick = vm::setupPick,
                            onBack = { vm.setupBack() },
                            onClose = { screen = Screen.SCHEDULE },
                            onRetry = vm::setupRetry,
                        )
                    }
                    Screen.SEARCH -> {
                        BackHandler { screen = Screen.SCHEDULE }
                        SearchScreen(
                            state = search,
                            favorites = favorites,
                            onKind = vm::setSearchKind,
                            onQuery = vm::setQuery,
                            onSubmit = vm::submitSearch,
                            onPick = { vm.open(it); screen = Screen.SCHEDULE },
                            onToggleFavorite = vm::toggleFavorite,
                            onBack = { screen = Screen.SCHEDULE },
                        )
                    }
                    Screen.USOS_LOGIN -> {
                        BackHandler { screen = Screen.SCHEDULE }
                        UsosLoginScreen(
                            onLoggedIn = { grades.onLoggedIn(); screen = Screen.SCHEDULE },
                            onBack = { screen = Screen.SCHEDULE },
                        )
                    }
                    Screen.SCHEDULE -> if (tab == MainTab.GRADES) {
                        BackHandler { tab = MainTab.SCHEDULE }
                        GradesScreen(
                            state = gradesState,
                            onOpen = grades::onOpen,
                            onSeen = grades::markSeen,
                            onLeave = grades::clearHighlight,
                            onRefresh = grades::refresh,
                            onLogin = { screen = Screen.USOS_LOGIN },
                            onLogout = grades::logout,
                            bottomBar = { MainTabsBar(tab, gradesState.unseen.size) { tab = it } },
                        )
                    } else if (tab == MainTab.SETTINGS) {
                        BackHandler { tab = MainTab.SCHEDULE }
                        SettingsScreen(
                            theme = themeMode,
                            groups = schedule.groups,
                            selectedGroups = schedule.selectedGroups,
                            onTheme = vm::setTheme,
                            onLanguage = { tag -> vm.chooseLanguage(tag); applyLanguage(tag) },
                            onChangeProgram = { vm.startSetup(); screen = Screen.SETUP },
                            onGroups = vm::setGroups,
                            update = update,
                            onCheckUpdates = { vm.checkForUpdate(manual = true) },
                            onInstallUpdate = vm::installUpdate,
                            bottomBar = { MainTabsBar(tab, gradesState.unseen.size) { tab = it } },
                        )
                    } else scheduleState.SaveableStateProvider("schedule") {
                        ScheduleScreen(
                            state = schedule,
                            favorites = favorites,
                            onHome = vm::openPrimary.takeIf { vm.primaryKey.let { it != null && it != schedule.target?.key } },
                            onToggleFavorite = vm::toggleFavorite,
                            onRefresh = vm::refresh,
                            onSearch = { vm.openSearch(); screen = Screen.SEARCH },
                            onSetup = { vm.startSetup(); screen = Screen.SETUP },
                            onGroups = vm::setGroups,
                            onDismissGroupPrompt = vm::dismissGroupPrompt,
                            tab = tab,
                            onTab = { tab = it },
                            newGrades = gradesState.unseen.size,
                            changes = changes,
                            onChangesSeen = vm::clearChanges,
                            update = update,
                            onInstallUpdate = vm::installUpdate,
                            onHideUpdate = vm::hideUpdateBanner,
                        )
                    }
                }
            }
        }
    }
}
