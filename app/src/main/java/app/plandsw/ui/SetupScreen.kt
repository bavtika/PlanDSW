package app.plandsw.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plandsw.R
import app.plandsw.data.Option
import app.plandsw.data.fold

private data class StepInfo(val number: Int, val title: String, @StringRes val hint: Int)

// Step names are the site's official Polish terms; we don't translate them.
private fun SetupStep.info() = when (this) {
    SetupStep.FACULTY -> StepInfo(1, "Wydział", R.string.setup_faculty_hint)
    SetupStep.KIERUNEK -> StepInfo(2, "Kierunek", R.string.setup_kierunek_hint)
    SetupStep.INTAKE -> StepInfo(3, "Nabór", R.string.setup_intake_hint)
    SetupStep.MODE -> StepInfo(4, "Tryb studiów", R.string.setup_mode_hint)
    SetupStep.TOK -> StepInfo(5, "Tok", R.string.setup_tok_hint)
}

@StringRes
private fun modeHint(name: String): Int? = when {
    name.startsWith("Niestacjonarne", ignoreCase = true) -> R.string.mode_part_time
    name.startsWith("Stacjonarne", ignoreCase = true) -> R.string.mode_full_time
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    state: SetupState,
    canClose: Boolean,
    onPick: (Option) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    val info = state.step.info()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                navigationIcon = {
                    when {
                        state.step != SetupStep.FACULTY ->
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                        canClose ->
                            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    stringResource(R.string.setup_step, info.number, if (state.step == SetupStep.TOK) 5 else 4),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(info.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(info.hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val chosen = listOfNotNull(state.faculty, state.kierunek, state.intake, state.mode)
                    .take(info.number - 1)
                if (chosen.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        chosen.joinToString("  ›  ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.loading && state.options.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    slideInHorizontally { if (forward) it else -it } togetherWith
                        slideOutHorizontally { if (forward) -it else it }
                },
                label = "setup-step",
            ) { step ->
                // Inside the animation, show the data of this specific step.
                val options = if (step == state.step) state.options else emptyList()
                when {
                    state.error != null && step == state.step ->
                        ErrorBlock(stringResource(state.error), onRetry, onBack.takeIf { step != SetupStep.FACULTY })
                    options.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.loading) CircularProgressIndicator()
                    }
                    else -> OptionList(step, options, state.loading, onPick)
                }
            }
        }
    }
}

@Composable
private fun OptionList(step: SetupStep, options: List<Option>, busy: Boolean, onPick: (Option) -> Unit) {
    var filter by rememberSaveable(step) { mutableStateOf("") }
    val showFilter = step == SetupStep.KIERUNEK && options.size > 8
    val shown = if (filter.isBlank()) options else {
        val words = filter.fold().split(' ').filter { it.isNotEmpty() }
        options.filter { o -> val name = o.name.fold(); words.all { it in name } }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (showFilter) {
            item {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.filter_hint)) },
                )
            }
        }
        items(shown, key = { it.id }) { option ->
            ListItem(
                headlineContent = { Text(option.name) },
                supportingContent = (if (step == SetupStep.MODE) modeHint(option.name) else null)?.let { hint -> { Text(stringResource(hint)) } },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.clickable(enabled = !busy) { onPick(option) },
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.nothing_found),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit, onBack: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.setup_change_choice)) }
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/** Tok group selection: chips with group codes as on the site, arranged by section. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupPickerSheet(
    groups: List<GroupChoice>,
    selected: Set<String>,
    firstTime: Boolean,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val res = LocalContext.current.resources
    var picked by rememberSaveable { mutableStateOf(selected.toList()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.groups_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(if (firstTime) R.string.groups_first_time else R.string.groups_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            groups.groupBy { groupCategory(it.short) }.toSortedMap().forEach { (category, list) ->
                Text(stringResource(category.title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { g ->
                        val checked = g.name in picked
                        FilterChip(
                            selected = checked,
                            onClick = { picked = if (checked) picked - g.name else picked + g.name },
                            label = { Text(groupCode(g.short)) },
                            leadingIcon = if (checked) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                        )
                    }
                }
                // Hint explaining what this section's codes mean.
                Text(
                    list.joinToString(" · ") { "${groupCode(it.short)} — ${groupDescription(it.short, res)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = { picked = groups.map { it.name } }) { Text(stringResource(R.string.action_all)) }
                Button(onClick = { onDone(picked.toSet()) }, enabled = picked.isNotEmpty()) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}
