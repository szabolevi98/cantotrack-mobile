package net.levente.cantotrack.mobile.ui.home

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.SavedQuery
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.User
import net.levente.cantotrack.mobile.data.timer.RunningClock
import net.levente.cantotrack.mobile.ui.components.Avatar
import net.levente.cantotrack.mobile.ui.components.ChoiceDialog
import net.levente.cantotrack.mobile.ui.components.ClockCard
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.EmptyState
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.components.HeaderToggle
import net.levente.cantotrack.mobile.ui.components.PriorityIcon
import net.levente.cantotrack.mobile.ui.components.StatusChip
import net.levente.cantotrack.mobile.ui.components.TypeIcon
import net.levente.cantotrack.mobile.ui.minutesText
import net.levente.cantotrack.mobile.ui.shortDate
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(
    viewModel: TicketsViewModel,
    user: User,
    clock: RunningClock?,
    onTicket: (String) -> Unit,
    onNewTicket: () -> Unit,
    onStopClock: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()
    var pickingProject by rememberSaveable { mutableStateOf(false) }
    var editingQuery by rememberSaveable { mutableStateOf(false) }

    // Back from a ticket, or from another app: read the list again, without a spinner.
    var resumedBefore by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedBefore) viewModel.load() else resumedBefore = true
        onPauseOrDispose { }
    }

    // Near the end of what is loaded, the next page.
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, state.tickets.size) { if (nearEnd) viewModel.loadMore() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CtHeader(
                title = stringResource(R.string.tickets_title),
                subtitle = state.todayMinutes?.let { stringResource(R.string.today_logged, minutesText(it)) },
                actions = {
                    IconButton(onClick = onSettings) { Avatar(user.initials) }
                },
            ) {
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    placeholder = { Text(stringResource(R.string.tickets_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.search.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearch("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val listView = state.view == TicketView.LIST
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderToggle(stringResource(R.string.tickets_mine), listView && state.mineOnly) { viewModel.setMineOnly(true) }
                    HeaderToggle(stringResource(R.string.tickets_all_open), listView && !state.mineOnly) { viewModel.setMineOnly(false) }
                }
                // The quick filters, in a row that scrolls sideways under the two above.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeaderToggle(stringResource(R.string.filter_starred), state.view == TicketView.STARRED, Icons.Rounded.Star) { viewModel.setView(TicketView.STARRED) }
                    HeaderToggle(stringResource(R.string.filter_recent), state.view == TicketView.RECENT, Icons.Rounded.History) { viewModel.setView(TicketView.RECENT) }
                    HeaderToggle(stringResource(R.string.filter_overdue), listView && state.due == "overdue", Icons.Rounded.WarningAmber) { viewModel.setDue("overdue") }
                    HeaderToggle(stringResource(R.string.filter_due_week), listView && state.due == "week", Icons.Rounded.Event) { viewModel.setDue("week") }
                    HeaderToggle(stringResource(R.string.filter_bugs), listView && state.bugsOnly, Icons.Rounded.BugReport) { viewModel.toggleBugs() }
                    HeaderToggle(
                        state.project ?: stringResource(R.string.filter_project),
                        listView && state.project != null,
                        Icons.Rounded.Folder,
                    ) { viewModel.loadProjects(); pickingProject = true }
                    HeaderToggle(
                        state.query?.name ?: stringResource(R.string.filter_query),
                        listView && state.query != null,
                        Icons.Rounded.FilterList,
                    ) { editingQuery = true }
                }
            }

            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (user.isGuest) 16.dp else 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (clock != null) {
                        item(key = "clock") { ClockCard(clock, onOpen = { onTicket(clock.ticket) }, onStop = onStopClock) }
                    }
                    state.error?.let { error -> item(key = "error") { ErrorBanner(error) } }

                    when {
                        state.loading -> item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        state.tickets.isEmpty() && state.error == null -> item(key = "empty") {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                EmptyState(
                                    Icons.Rounded.Inbox,
                                    stringResource(
                                        when {
                                            state.view == TicketView.STARRED -> R.string.starred_empty_title
                                            state.view == TicketView.RECENT -> R.string.recent_empty_title
                                            state.search.isNotBlank() || state.filtered -> R.string.tickets_nothing_found
                                            else -> R.string.tickets_empty_title
                                        },
                                    ),
                                    stringResource(
                                        when {
                                            state.view == TicketView.STARRED -> R.string.starred_empty_text
                                            state.view == TicketView.RECENT -> R.string.recent_empty_text
                                            state.filtered -> R.string.tickets_empty_filtered
                                            state.mineOnly -> R.string.tickets_empty_mine
                                            else -> R.string.tickets_empty_all
                                        },
                                    ),
                                )
                                if (state.filtered) {
                                    TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.filter_clear)) }
                                }
                            }
                        }
                        else -> {
                            item(key = "count") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when (state.view) {
                                            TicketView.STARRED -> stringResource(R.string.starred_count, state.total)
                                            TicketView.RECENT -> stringResource(R.string.recent_count)
                                            TicketView.LIST -> stringResource(R.string.tickets_count, state.total)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.muted,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (state.filtered) {
                                        TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.filter_clear)) }
                                    }
                                }
                            }
                            items(state.tickets, key = { it.key }) { ticket ->
                                TicketRow(ticket, running = clock?.ticket == ticket.key, onClick = { onTicket(ticket.key) })
                            }
                            if (state.loadingMore) {
                                item(key = "more") {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!user.isGuest) {
            ExtendedFloatingActionButton(
                onClick = onNewTicket,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_ticket)) },
                containerColor = colors.primary,
                contentColor = if (colors.dark) Color(0xFF0B1628) else Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (pickingProject) {
        val projects = state.projects
        if (projects == null) {
            AlertDialog(
                onDismissRequest = { pickingProject = false },
                title = { Text(stringResource(R.string.filter_project)) },
                text = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pickingProject = false }) { Text(stringResource(R.string.cancel)) } },
            )
        } else {
            ChoiceDialog(
                title = stringResource(R.string.filter_project),
                options = listOf<String?>(null) + projects.map { it.code },
                selected = state.project,
                label = { code -> code?.let { c -> "$c · " + projects.first { it.code == c }.name } ?: stringResource(R.string.filter_every_project) },
                onPick = { pickingProject = false; viewModel.setProject(it) },
                onDismiss = { pickingProject = false },
            )
        }
    }

    if (editingQuery) {
        QueryDialog(
            saved = state.savedQueries,
            active = state.query,
            onApply = { editingQuery = false; viewModel.setQuery(it) },
            onSave = { editingQuery = false; viewModel.saveQuery(it) },
            onForget = viewModel::forgetQuery,
            onDismiss = { editingQuery = false },
        )
    }
}

/**
 * The ticket list's query language, as on the web: a query typed once and
 * kept under a name, or tried without one.
 */
@Composable
private fun QueryDialog(
    saved: List<SavedQuery>,
    active: SavedQuery?,
    onApply: (SavedQuery?) -> Unit,
    onSave: (SavedQuery) -> Unit,
    onForget: (SavedQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CtTheme.colors
    var text by rememberSaveable { mutableStateOf(active?.query.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(active?.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.query_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.forEach { query ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onApply(query) }, modifier = Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(query.name, style = MaterialTheme.typography.titleSmall, color = if (query == active) colors.primary else colors.text)
                                Text(query.query, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { onForget(query) }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = colors.muted) }
                    }
                }
                if (saved.isNotEmpty()) HorizontalDivider(color = colors.border)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.query_text)) },
                    placeholder = { Text("priority = high AND due < ${LocalDate.now().plusDays(7)}") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.query_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.query_help), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    val query = SavedQuery(name.trim().ifEmpty { text.trim().take(24) }, text.trim())
                    if (name.isNotBlank()) onSave(query) else onApply(query)
                },
            ) { Text(stringResource(if (name.isNotBlank()) R.string.query_save else R.string.query_apply)) }
        },
        dismissButton = {
            Row {
                if (active != null) TextButton(onClick = { onApply(null) }) { Text(stringResource(R.string.query_off)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun TicketRow(ticket: Ticket, running: Boolean, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val colors = CtTheme.colors
    CtCard(
        onClick = onClick,
        border = if (running) colors.success else colors.border,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeIcon(ticket.type)
            Spacer(Modifier.width(6.dp))
            Text(ticket.key, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            Spacer(Modifier.width(6.dp))
            PriorityIcon(ticket.priority)
            Spacer(Modifier.weight(1f))
            if (trailing != null) trailing() else StatusChip(ticket.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(ticket.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val hours = hoursLine(ticket)
        val due = ticket.dueOn?.takeIf { !ticket.isDone }
        if (hours != null || ticket.assignee != null || due != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(ticket.assignee?.name, hours).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (due != null) {
                    val late = due < LocalDate.now().toString()
                    Icon(Icons.Rounded.Event, null, tint = if (late) colors.danger else colors.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(shortDate(due), style = MaterialTheme.typography.bodySmall, color = if (late) colors.danger else colors.muted)
                }
            }
        }
    }
}

/** "2 h logged, 1 h 30 m left", or what of it there is. */
@Composable
private fun hoursLine(ticket: Ticket): String? {
    val parts = buildList {
        if (ticket.loggedMinutes > 0) add(stringResource(R.string.ticket_logged, minutesText(ticket.loggedMinutes)))
        ticket.remainingMinutes?.takeIf { it > 0 }?.let { add(stringResource(R.string.ticket_remaining, minutesText(it))) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
