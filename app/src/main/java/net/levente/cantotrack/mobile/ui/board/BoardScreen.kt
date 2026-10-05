package net.levente.cantotrack.mobile.ui.board

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import net.levente.cantotrack.mobile.data.api.BoardColumn
import net.levente.cantotrack.mobile.data.api.Sprint
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.User
import net.levente.cantotrack.mobile.data.timer.RunningClock
import net.levente.cantotrack.mobile.ui.components.ChoiceDialog
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.EmptyState
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.components.HeaderToggle
import net.levente.cantotrack.mobile.ui.components.SectionLabel
import net.levente.cantotrack.mobile.ui.components.StatusChip
import net.levente.cantotrack.mobile.ui.home.TicketRow
import net.levente.cantotrack.mobile.ui.shortDate
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    viewModel: BoardViewModel,
    user: User,
    clock: RunningClock?,
    snackbar: SnackbarHostState,
    onTicket: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    var pickingBoard by rememberSaveable { mutableStateOf(false) }

    var resumedBefore by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedBefore) viewModel.load(refreshing = false) else resumedBefore = true
        onPauseOrDispose { }
    }

    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    val board = state.board
    Column(Modifier.fillMaxSize()) {
        CtHeader(
            title = board?.name ?: stringResource(R.string.board_title),
            subtitle = board?.let {
                if (it.shared) stringResource(R.string.board_shared, it.projects.joinToString(", ")) else it.projects.joinToString(", ")
            },
            actions = {
                if ((state.boards?.size ?: 0) > 1) {
                    Surface(onClick = { pickingBoard = true }, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.12f)) {
                        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.board_other), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Rounded.ArrowDropDown, null, tint = Color.White)
                        }
                    }
                }
            },
        ) {
            val open = board?.sprints?.filter { !it.isClosed }.orEmpty()
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderToggle(stringResource(R.string.tickets_mine), state.mineOnly, Icons.Rounded.Person) { viewModel.setMineOnly(!state.mineOnly) }
                open.forEach { sprint ->
                    HeaderToggle(sprint.name, sprint.id == state.sprintId) { viewModel.selectSprint(sprint.id) }
                }
            }
        }

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                val sprint = state.sprint
                when {
                    state.loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.boards?.isEmpty() == true -> item(key = "no-boards") {
                        EmptyState(Icons.Rounded.ViewKanban, stringResource(R.string.board_none_title), stringResource(R.string.board_none_text))
                    }
                    board != null && sprint == null -> item(key = "no-sprint") {
                        EmptyState(Icons.Rounded.ViewKanban, stringResource(R.string.board_no_sprint_title), stringResource(R.string.board_no_sprint_text))
                    }
                    sprint != null -> {
                        item(key = "sprint") { SprintCard(sprint) }
                        if (state.tickets.isEmpty() && state.error == null) {
                            item(key = "empty") {
                                Text(
                                    stringResource(if (state.mineOnly) R.string.board_empty_mine else R.string.board_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                        state.byColumn().forEachIndexed { index, (column, tickets) ->
                            item(key = "col-$index") {
                                ColumnLabel(column?.name ?: stringResource(R.string.board_other_column), tickets.size, column?.done == true)
                            }
                            items(tickets, key = { it.key }) { ticket ->
                                TicketRow(
                                    ticket,
                                    running = clock?.ticket == ticket.key,
                                    onClick = { onTicket(ticket.key) },
                                    trailing = {
                                        MoveButton(
                                            ticket = ticket,
                                            columns = board?.columns.orEmpty(),
                                            moving = state.moving == ticket.key,
                                            enabled = !user.isGuest,
                                            onMove = { viewModel.move(ticket, it) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickingBoard) {
        val boards = state.boards.orEmpty()
        ChoiceDialog(
            title = stringResource(R.string.board_pick),
            options = boards.map { it.id },
            selected = board?.id,
            label = { id -> boards.first { it.id == id }.let { b -> if (b.shared) "${b.name} (${b.projects.joinToString(", ")})" else b.name } },
            onPick = { pickingBoard = false; viewModel.selectBoard(it) },
            onDismiss = { pickingBoard = false },
        )
    }
}

/** The sprint: its dates, its goal, and how far it has got by tickets and by points. */
@Composable
private fun SprintCard(sprint: Sprint) {
    val colors = CtTheme.colors
    CtCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sprint.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (sprint.isActive) R.string.sprint_active else R.string.sprint_planned),
                style = MaterialTheme.typography.labelMedium,
                color = if (sprint.isActive) colors.success else colors.muted,
            )
        }
        if (sprint.startsOn != null && sprint.endsOn != null) {
            val left = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(sprint.endsOn))
            Text(
                listOfNotNull(
                    "${shortDate(sprint.startsOn)} – ${shortDate(sprint.endsOn)}",
                    when {
                        !sprint.isActive -> null
                        left > 0 -> stringResource(R.string.sprint_days_left, left)
                        left == 0L -> stringResource(R.string.sprint_ends_today)
                        else -> stringResource(R.string.sprint_overdue)
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        sprint.goal?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        sprint.tickets?.let { tickets ->
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (tickets.count == 0) 0f else tickets.done.toFloat() / tickets.count },
                color = colors.success,
                trackColor = colors.successSoft,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(
                    stringResource(R.string.sprint_tickets_done, tickets.done, tickets.count),
                    sprint.points?.takeIf { it.total > 0 }?.let { stringResource(R.string.sprint_points_done, it.done, it.total) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun ColumnLabel(name: String, count: Int, done: Boolean) {
    val colors = CtTheme.colors
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("$name · $count")
        if (done) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.Check, null, tint = colors.success, modifier = Modifier.size(14.dp))
        }
    }
}

/** The ticket's status, which opens the board's other columns to move it to. */
@Composable
private fun MoveButton(ticket: Ticket, columns: List<BoardColumn>, moving: Boolean, enabled: Boolean, onMove: (BoardColumn) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        // The chip's own height: the card is the big target, this is a small one on it.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Surface(onClick = { open = true }, enabled = enabled && !moving, shape = RoundedCornerShape(6.dp), color = Color.Transparent) {
                if (moving) {
                    CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
                } else {
                    StatusChip(ticket.status, trailing = if (enabled) Icons.Rounded.SwapVert else null)
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            columns.forEach { column ->
                val here = ticket.status.id in column.statusIds
                DropdownMenuItem(
                    text = { Text(column.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { if (here) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                    enabled = !here,
                    onClick = { open = false; onMove(column) },
                )
            }
        }
    }
}
