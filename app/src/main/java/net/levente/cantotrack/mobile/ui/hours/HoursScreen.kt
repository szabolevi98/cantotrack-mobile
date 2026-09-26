package net.levente.cantotrack.mobile.ui.hours

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Worklog
import net.levente.cantotrack.mobile.data.timer.RunningClock
import net.levente.cantotrack.mobile.ui.components.ClockCard
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.EmptyState
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.dayText
import net.levente.cantotrack.mobile.ui.minutesText
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoursScreen(
    viewModel: HoursViewModel,
    clock: RunningClock?,
    snackbar: SnackbarHostState,
    onTicket: (String) -> Unit,
    onStopClock: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    var deleting by remember { mutableStateOf<Worklog?>(null) }

    var resumedBefore by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedBefore) viewModel.load() else resumedBefore = true
        onPauseOrDispose { }
    }

    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Column(Modifier.fillMaxSize()) {
        val short = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        CtHeader(
            title = stringResource(R.string.hours_title),
            subtitle = stringResource(R.string.hours_week, state.monday.format(short), state.sunday.format(short)),
            actions = {
                IconButton(onClick = viewModel::previousWeek) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.hours_previous), tint = Color.White)
                }
                IconButton(onClick = viewModel::nextWeek, enabled = !state.isThisWeek) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        stringResource(R.string.hours_next),
                        tint = if (state.isThisWeek) Color.White.copy(alpha = 0.3f) else Color.White,
                    )
                }
            },
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(minutesText(state.total), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.hours_this_week_total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.sidebarText,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                if (!state.isThisWeek) {
                    TextButton(onClick = viewModel::thisWeek) { Text(stringResource(R.string.hours_back_to_this_week), color = Color.White) }
                }
            }
        }

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (clock != null && state.isThisWeek) {
                    item(key = "clock") { ClockCard(clock, onOpen = { onTicket(clock.ticket) }, onStop = onStopClock) }
                }
                state.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                when {
                    state.loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.days.isEmpty() && state.error == null -> item(key = "empty") {
                        EmptyState(Icons.Rounded.Schedule, stringResource(R.string.hours_empty_title), stringResource(R.string.hours_empty_text))
                    }
                    else -> items(state.days, key = { it.date.toString() }) { day ->
                        DayCard(day, onTicket = onTicket, onDelete = { deleting = it })
                    }
                }
                item(key = "hint") {
                    Text(
                        stringResource(R.string.hours_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.hours_delete_title)) },
            text = { Text(stringResource(R.string.hours_delete_text, minutesText(entry.minutes), entry.ticket)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entry); deleting = null }) {
                    Text(stringResource(R.string.delete), color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCard(day: Day, onTicket: (String) -> Unit, onDelete: (Worklog) -> Unit) {
    val colors = CtTheme.colors
    val today = day.date == LocalDate.now()
    CtCard(modifier = Modifier.fillMaxWidth(), border = if (today) colors.primary.copy(alpha = 0.4f) else colors.border, contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (today) stringResource(R.string.hours_today) else dayText(day.date),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (day.minutes == 0) "—" else minutesText(day.minutes),
                style = MaterialTheme.typography.titleSmall,
                color = if (day.minutes == 0) colors.muted else colors.text,
            )
        }
        day.entries.forEach { entry ->
            HorizontalDivider(color = colors.border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onTicket(entry.ticket) }, onLongClick = { onDelete(entry) })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(entry.ticket, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        entry.start?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                        entry.workType?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    entry.note?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(minutesText(entry.minutes), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (day.entries.isEmpty()) {
            HorizontalDivider(color = colors.border)
            Text(
                stringResource(R.string.hours_nothing_that_day),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
