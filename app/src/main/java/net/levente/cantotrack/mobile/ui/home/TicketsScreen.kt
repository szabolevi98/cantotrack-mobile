package net.levente.cantotrack.mobile.ui.home

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.User
import net.levente.cantotrack.mobile.data.timer.RunningClock
import net.levente.cantotrack.mobile.ui.components.Avatar
import net.levente.cantotrack.mobile.ui.components.ClockCard
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.EmptyState
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.components.PriorityIcon
import net.levente.cantotrack.mobile.ui.components.StatusChip
import net.levente.cantotrack.mobile.ui.components.TypeIcon
import net.levente.cantotrack.mobile.ui.minutesText
import net.levente.cantotrack.mobile.ui.theme.CtTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(
    viewModel: TicketsViewModel,
    user: User,
    clock: RunningClock?,
    onTicket: (String) -> Unit,
    onStopClock: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()

    // Back from a ticket, or from another app: read the list again, without a spinner.
    var resumedBefore by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedBefore) viewModel.load() else resumedBefore = true
        onPauseOrDispose { }
    }

    // Near the end of what is loaded, the next page.
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, state.tickets.size) { if (nearEnd) viewModel.loadMore() }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderChip(stringResource(R.string.tickets_mine), state.mineOnly) { viewModel.setMineOnly(true) }
                HeaderChip(stringResource(R.string.tickets_all_open), !state.mineOnly) { viewModel.setMineOnly(false) }
            }
        }

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(16.dp),
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
                        EmptyState(
                            Icons.Rounded.Inbox,
                            stringResource(if (state.search.isBlank()) R.string.tickets_empty_title else R.string.tickets_nothing_found),
                            stringResource(if (state.mineOnly) R.string.tickets_empty_mine else R.string.tickets_empty_all),
                        )
                    }
                    else -> {
                        item(key = "count") {
                            Text(
                                stringResource(R.string.tickets_count, state.total),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
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
}

@Composable
private fun HeaderChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = CtTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = colors.sidebarText,
            selectedContainerColor = Color.White.copy(alpha = 0.18f),
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.White.copy(alpha = 0.25f),
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun TicketRow(ticket: Ticket, running: Boolean, onClick: () -> Unit) {
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
            StatusChip(ticket.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(ticket.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val hours = hoursLine(ticket)
        if (hours != null || ticket.assignee != null) {
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    listOfNotNull(ticket.assignee?.name, hours).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
