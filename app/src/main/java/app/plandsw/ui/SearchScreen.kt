package app.plandsw.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plandsw.R
import app.plandsw.data.Target
import app.plandsw.data.TargetKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState,
    favorites: List<Target>,
    onKind: (TargetKind) -> Unit,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onPick: (Target) -> Unit,
    onToggleFavorite: (Target) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                val kinds = listOf(TargetKind.TEACHER, TargetKind.ROOM)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { i, kind ->
                        SegmentedButton(
                            selected = state.kind == kind,
                            onClick = { onKind(kind) },
                            shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                            colors = segmentedColors(),
                            icon = {},
                        ) {
                            Text(
                                stringResource(if (kind == TargetKind.ROOM) R.string.search_room else R.string.search_teacher),
                                maxLines = 1,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear)) }
                        }
                    },
                    placeholder = {
                        Text(
                            stringResource(if (state.kind == TargetKind.ROOM) R.string.search_room_hint else R.string.search_teacher_hint)
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                if (state.loading) LoadingBar(Modifier.fillMaxWidth())
            }
            when {
                state.error != null -> Message(stringResource(state.error))
                state.searched && state.isEmpty && !state.loading -> Message(stringResource(R.string.nothing_found))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl)) {
                    // Favourites, then teachers from your schedule, then everyone else.
                    state.sections.forEach { section ->
                        section.title?.let { title -> item(key = "h_$title") { SectionHeader(stringResource(title)) } }
                        items(section.items, key = { "${section.title}_${it.key}" }) { t ->
                            TargetRow(t, favorites.any { it.key == t.key }, onPick, onToggleFavorite)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs),
    )
}

@Composable
private fun TargetRow(t: Target, fav: Boolean, onPick: (Target) -> Unit, onToggleFavorite: (Target) -> Unit) {
    ListItem(
        headlineContent = { Text(t.name) },
        supportingContent = if (t.subtitle.isNotBlank()) {
            { Text(t.subtitle) }
        } else null,
        leadingContent = { Icon(kindIcon(t.kind), null) },
        trailingContent = {
            IconButton(onClick = { onToggleFavorite(t) }) {
                if (fav) Icon(Icons.Filled.Star, stringResource(R.string.favorite_remove), tint = MaterialTheme.colorScheme.secondary)
                else Icon(Icons.Outlined.StarOutline, stringResource(R.string.favorite_add))
            }
        },
        modifier = Modifier.clickable { onPick(t) },
    )
    HorizontalDivider(Modifier.padding(start = 56.dp))
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xxl),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
