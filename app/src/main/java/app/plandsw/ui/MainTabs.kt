package app.plandsw.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.plandsw.R

enum class MainTab { SCHEDULE, SUBJECTS, GRADES, SETTINGS }

/** Bottom tabs: schedule, subjects, grades, settings; grades show a new-items counter. */
@Composable
fun MainTabsBar(selected: MainTab, newGrades: Int, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == MainTab.SCHEDULE,
            onClick = { onSelect(MainTab.SCHEDULE) },
            icon = { Icon(Icons.Outlined.CalendarMonth, null) },
            label = { Text(stringResource(R.string.tab_schedule)) },
        )
        NavigationBarItem(
            selected = selected == MainTab.SUBJECTS,
            onClick = { onSelect(MainTab.SUBJECTS) },
            icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
            label = { Text(stringResource(R.string.tab_subjects)) },
        )
        NavigationBarItem(
            selected = selected == MainTab.GRADES,
            onClick = { onSelect(MainTab.GRADES) },
            icon = {
                BadgedBox(badge = { if (newGrades > 0) Badge { Text(newGrades.toString()) } }) {
                    Icon(Icons.Outlined.Grade, null)
                }
            },
            label = { Text(stringResource(R.string.grades_title)) },
        )
        NavigationBarItem(
            selected = selected == MainTab.SETTINGS,
            onClick = { onSelect(MainTab.SETTINGS) },
            icon = { Icon(Icons.Outlined.Settings, null) },
            label = { Text(stringResource(R.string.settings_title)) },
        )
    }
}
