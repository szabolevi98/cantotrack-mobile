package net.levente.cantotrack.mobile.ui.ticket

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Comment
import net.levente.cantotrack.mobile.data.api.Status
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.User
import net.levente.cantotrack.mobile.data.timer.RunningClock
import net.levente.cantotrack.mobile.ui.ClockActions
import net.levente.cantotrack.mobile.ui.components.ClockCard
import net.levente.cantotrack.mobile.ui.components.CtButton
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.components.PriorityIcon
import net.levente.cantotrack.mobile.ui.components.SectionLabel
import net.levente.cantotrack.mobile.ui.components.StatusChip
import net.levente.cantotrack.mobile.ui.components.TypeIcon
import net.levente.cantotrack.mobile.ui.components.WorklogDialog
import net.levente.cantotrack.mobile.ui.components.rememberNotificationPermission
import net.levente.cantotrack.mobile.ui.dateTimeText
import net.levente.cantotrack.mobile.ui.minutesText
import net.levente.cantotrack.mobile.ui.theme.CtTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScreen(
    viewModel: TicketViewModel,
    user: User,
    clock: RunningClock?,
    clockActions: ClockActions,
    snackbar: SnackbarHostState,
    onStopClock: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    val context = LocalContext.current
    var logging by rememberSaveable { mutableStateOf(false) }
    val askForNotifications = rememberNotificationPermission()

    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    val ticket = state.ticket
    val runningHere = clock?.ticket == viewModel.key

    Column(Modifier.fillMaxSize()) {
        CtHeader(
            title = viewModel.key,
            subtitle = ticket?.let { listOfNotNull(it.epic?.title, it.sprint).joinToString(" · ").ifEmpty { null } },
            onBack = onBack,
            actions = {
                ticket?.url?.let { url ->
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.ticket_open_on_web), tint = Color.White)
                    }
                }
            },
        )

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                if (ticket != null) {
                    item(key = "summary") { Summary(ticket, state.changingStatus, state.statuses, viewModel::loadStatuses, viewModel::changeStatus) }

                    if (!user.isGuest) {
                        item(key = "clock") {
                            if (runningHere && clock != null) {
                                ClockCard(clock, onOpen = {}, onStop = onStopClock)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CtButton(
                                        stringResource(if (clock == null) R.string.timer_start else R.string.timer_switch_here),
                                        onClick = { askForNotifications(); clockActions.start(viewModel.key) },
                                        loading = clockActions.busy,
                                        icon = Icons.Rounded.PlayArrow,
                                        color = colors.success,
                                        modifier = Modifier.weight(1f),
                                    )
                                    CtButton(
                                        stringResource(R.string.worklog_log),
                                        onClick = { logging = true },
                                        icon = Icons.Rounded.EditCalendar,
                                        outlined = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    if (!ticket.description.isNullOrBlank()) {
                        item(key = "description") {
                            CtCard(Modifier.fillMaxWidth()) {
                                SectionLabel(stringResource(R.string.ticket_description))
                                Spacer(Modifier.height(8.dp))
                                Text(plainText(ticket.description), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }

                    item(key = "comments-label") {
                        SectionLabel(stringResource(R.string.ticket_comments, state.comments.size), Modifier.padding(top = 4.dp))
                    }
                    if (state.comments.isEmpty()) {
                        item(key = "no-comments") {
                            Text(stringResource(R.string.ticket_no_comments), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                    }
                    items(state.comments, key = { it.id }) { CommentCard(it, mine = it.author.id == user.id) }
                }
            }
        }

        if (ticket != null) {
            CommentBar(sending = state.sendingComment, onSend = viewModel::addComment)
        }
    }

    if (logging) {
        WorklogDialog(
            title = stringResource(R.string.worklog_title),
            confirmLabel = stringResource(R.string.worklog_log),
            busy = state.logging,
            onConfirm = { minutes, date, note -> viewModel.logWork(minutes, date, note) { if (it) logging = false } },
            onDismiss = { logging = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(
    ticket: Ticket,
    changing: Boolean,
    statuses: List<Status>,
    onOpenStatuses: () -> Unit,
    onStatus: (Status) -> Unit,
) {
    val colors = CtTheme.colors
    var menu by remember { mutableStateOf(false) }
    CtCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeIcon(ticket.type, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            PriorityIcon(ticket.priority, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            Text(priorityName(ticket.priority), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            Spacer(Modifier.weight(1f))
            Box {
                Surface(
                    onClick = { onOpenStatuses(); menu = true },
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                ) {
                    if (changing) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
                    } else {
                        StatusChip(ticket.status, trailing = Icons.Rounded.ArrowDropDown)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (statuses.isEmpty()) {
                        DropdownMenuItem(text = { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }, onClick = {}, enabled = false)
                    }
                    statuses.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.name) },
                            leadingIcon = {
                                if (status.name == ticket.status.name) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp))
                            },
                            onClick = { menu = false; onStatus(status) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(ticket.title, style = MaterialTheme.typography.titleLarge)
        if (ticket.labels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ticket.labels.forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.slate,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.slateSoft).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(12.dp))
        Row {
            Fact(stringResource(R.string.ticket_assignee), ticket.assignee?.name ?: stringResource(R.string.ticket_unassigned), Modifier.weight(1f))
            Fact(stringResource(R.string.ticket_due), ticket.dueOn ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Fact(stringResource(R.string.ticket_estimate), ticket.estimateMinutes?.let { minutesText(it) } ?: "—", Modifier.weight(1f))
            Fact(stringResource(R.string.ticket_logged_label), minutesText(ticket.loggedMinutes), Modifier.weight(1f))
            Fact(stringResource(R.string.ticket_remaining_label), ticket.remainingMinutes?.let { minutesText(it) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CtTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun priorityName(priority: String): String = stringResource(
    when (priority) {
        "urgent" -> R.string.priority_urgent
        "high" -> R.string.priority_high
        "low" -> R.string.priority_low
        else -> R.string.priority_medium
    },
)

@Composable
private fun CommentCard(comment: Comment, mine: Boolean) {
    val colors = CtTheme.colors
    CtCard(Modifier.fillMaxWidth(), border = if (mine) colors.primary.copy(alpha = 0.3f) else colors.border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(comment.author.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(dateTimeText(comment.createdAt), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Spacer(Modifier.height(6.dp))
        Text(plainText(comment.body), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The field at the bottom, over the keyboard when it is open. */
@Composable
private fun CommentBar(sending: Boolean, onSend: (String, (Boolean) -> Unit) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val colors = CtTheme.colors
    Surface(color = colors.surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(5000) },
                placeholder = { Text(stringResource(R.string.ticket_comment_placeholder)) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { onSend(text) { if (it) text = "" } }, enabled = text.isNotBlank() && !sending) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.ticket_comment_send), tint = if (text.isNotBlank()) colors.primary else colors.muted)
                }
            }
        }
    }
}

/**
 * Markdown as plain text: the web draws it, the phone shows what it says.
 * Checkboxes become ticks, and emphasis marks go.
 */
internal fun plainText(markdown: String): String = markdown
    .replace(Regex("(?m)^(\\s*)[-*] \\[[xX]\\] "), "$1☑ ")
    .replace(Regex("(?m)^(\\s*)[-*] \\[ \\] "), "$1☐ ")
    .replace(Regex("(?m)^(\\s*)[-*] "), "$1• ")
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("`([^`]+)`"), "$1")
    .trim()
