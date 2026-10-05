package net.levente.cantotrack.mobile.ui.ticket

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Attachment
import net.levente.cantotrack.mobile.data.api.Comment
import net.levente.cantotrack.mobile.data.api.Person
import net.levente.cantotrack.mobile.data.api.Status
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.User
import net.levente.cantotrack.mobile.data.api.Worklog
import net.levente.cantotrack.mobile.data.timer.RunningClock
import net.levente.cantotrack.mobile.ui.ClockActions
import net.levente.cantotrack.mobile.ui.PRIORITIES
import net.levente.cantotrack.mobile.ui.components.ChoiceDialog
import net.levente.cantotrack.mobile.ui.components.ClockCard
import net.levente.cantotrack.mobile.ui.components.CtButton
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.DatePickDialog
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.components.PersonPickerDialog
import net.levente.cantotrack.mobile.ui.components.PriorityIcon
import net.levente.cantotrack.mobile.ui.components.SectionLabel
import net.levente.cantotrack.mobile.ui.components.StatusChip
import net.levente.cantotrack.mobile.ui.components.TypeIcon
import net.levente.cantotrack.mobile.ui.components.WorklogDialog
import net.levente.cantotrack.mobile.ui.components.rememberNotificationPermission
import net.levente.cantotrack.mobile.ui.components.rememberPhotoSource
import net.levente.cantotrack.mobile.ui.dateTimeText
import net.levente.cantotrack.mobile.ui.linkName
import net.levente.cantotrack.mobile.ui.minutesText
import net.levente.cantotrack.mobile.ui.priorityName
import net.levente.cantotrack.mobile.ui.shortDate
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScreen(
    viewModel: TicketViewModel,
    user: User,
    clock: RunningClock?,
    clockActions: ClockActions,
    snackbar: SnackbarHostState,
    onStopClock: () -> Unit,
    onTicket: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    val context = LocalContext.current
    var logging by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pickingPerson by rememberSaveable { mutableStateOf(false) }
    var pickingDue by rememberSaveable { mutableStateOf(false) }
    var pickingSprint by rememberSaveable { mutableStateOf(false) }
    var editingText by rememberSaveable { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<Comment?>(null) }
    var deletingComment by remember { mutableStateOf<Comment?>(null) }
    var deletingFile by remember { mutableStateOf<Attachment?>(null) }
    val askForNotifications = rememberNotificationPermission()
    val noCamera = stringResource(R.string.attachment_no_camera)
    val source = rememberPhotoSource(onPicked = viewModel::upload, onNoCamera = { viewModel.messageFrom(noCamera) })
    val member = !user.isGuest

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
                if (ticket != null) {
                    ticket.starred?.let { starred ->
                        IconButton(onClick = viewModel::toggleStar) {
                            Icon(
                                if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                stringResource(if (starred) R.string.ticket_unstar else R.string.ticket_star),
                                tint = if (starred) Color(0xFFFFC857) else Color.White,
                            )
                        }
                    }
                    ticket.watching?.let { watching ->
                        IconButton(onClick = viewModel::toggleWatch) {
                            Icon(
                                if (watching) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                stringResource(if (watching) R.string.ticket_unwatch else R.string.ticket_watch),
                                tint = if (watching) Color.White else Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more), tint = Color.White) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            ticket.url?.let { url ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ticket_open_on_web)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                    onClick = { menu = false; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                                )
                            }
                            if (member) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ticket_edit_text)) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menu = false; editingText = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.attachment_camera)) },
                                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                                onClick = { menu = false; source.camera() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.attachment_gallery)) },
                                leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                                onClick = { menu = false; source.gallery() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.attachment_file)) },
                                leadingIcon = { Icon(Icons.Rounded.AttachFile, null) },
                                onClick = { menu = false; source.file() },
                            )
                        }
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
                    item(key = "summary") {
                        Summary(
                            ticket = ticket,
                            user = user,
                            changing = state.changingStatus,
                            saving = state.saving,
                            statuses = state.statuses,
                            onOpenStatuses = viewModel::loadStatuses,
                            onStatus = viewModel::changeStatus,
                            onPriority = viewModel::setPriority,
                            onAssignee = { viewModel.loadPeople(); pickingPerson = true },
                            onTake = { viewModel.takeIt(Person(user.id, user.name)) },
                            onDue = { pickingDue = true },
                            onClearDue = { viewModel.setDue(null) },
                            onSprint = { viewModel.loadSprints(); pickingSprint = true },
                            onParent = onTicket,
                        )
                    }

                    if (member) {
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
                                        onClick = { viewModel.loadWorkTypes(); logging = true },
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

                    if (state.subtasks.isNotEmpty()) {
                        item(key = "subtasks") {
                            CtCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
                                SectionLabel(
                                    stringResource(R.string.ticket_subtasks, ticket.subtasks.done, ticket.subtasks.count),
                                    Modifier.padding(horizontal = 16.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                state.subtasks.forEach { sub ->
                                    SmallTicketRow(sub.key, sub.title, sub.status, sub.type) { onTicket(sub.key) }
                                }
                            }
                        }
                    }

                    if (state.links.isNotEmpty()) {
                        item(key = "links") {
                            CtCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
                                SectionLabel(stringResource(R.string.ticket_links), Modifier.padding(horizontal = 16.dp))
                                Spacer(Modifier.height(4.dp))
                                state.links.forEach { link ->
                                    SmallTicketRow(link.ticket, link.title, Status(name = link.status.name, category = link.status.category), null, linkName(link.kind)) {
                                        onTicket(link.ticket)
                                    }
                                }
                            }
                        }
                    }

                    item(key = "attachments") {
                        Attachments(
                            attachments = state.attachments,
                            uploading = state.uploading,
                            opening = state.opening,
                            thumbnail = viewModel::thumbnail,
                            onOpen = viewModel::open,
                            onDelete = { a -> if (a.author?.id == user.id || user.isAdmin) deletingFile = a },
                            onAdd = source,
                        )
                    }

                    if (state.worklogs.isNotEmpty()) {
                        item(key = "hours") { Hours(state.worklogs) }
                    }

                    item(key = "comments-label") {
                        SectionLabel(stringResource(R.string.ticket_comments, state.comments.size), Modifier.padding(top = 4.dp))
                    }
                    if (state.comments.isEmpty()) {
                        item(key = "no-comments") {
                            Text(stringResource(R.string.ticket_no_comments), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                    }
                    items(state.comments, key = { it.id }) { comment ->
                        val mine = comment.author.id == user.id
                        CommentCard(
                            comment,
                            mine = mine,
                            onEdit = if (mine) ({ editingComment = comment }) else null,
                            onDelete = if (mine || user.isAdmin) ({ deletingComment = comment }) else null,
                        )
                    }
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
            workTypes = state.workTypes,
            onConfirm = { minutes, date, note, workType -> viewModel.logWork(minutes, date, note, workType) { if (it) logging = false } },
            onDismiss = { logging = false },
        )
    }

    if (pickingPerson && ticket != null) {
        PersonPickerDialog(
            people = state.people,
            meId = user.id,
            current = ticket.assignee?.id,
            onPick = { pickingPerson = false; viewModel.setAssignee(it) },
            onDismiss = { pickingPerson = false },
        )
    }

    if (pickingDue && ticket != null) {
        DatePickDialog(
            initial = ticket.dueOn?.let(LocalDate::parse),
            onPick = { pickingDue = false; viewModel.setDue(it.toString()) },
            onDismiss = { pickingDue = false },
        )
    }

    if (pickingSprint && ticket != null) {
        val sprints = state.sprints
        if (sprints == null) {
            AlertDialog(
                onDismissRequest = { pickingSprint = false },
                title = { Text(stringResource(R.string.ticket_sprint)) },
                text = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pickingSprint = false }) { Text(stringResource(R.string.cancel)) } },
            )
        } else {
            ChoiceDialog(
                title = stringResource(R.string.ticket_sprint),
                options = listOf<SprintOption?>(null) + sprints,
                selected = sprints.firstOrNull { it.name == ticket.sprint },
                label = { option ->
                    option?.let {
                        it.name + " · " + it.board + if (it.active) " · " + stringResource(R.string.sprint_active) else ""
                    } ?: stringResource(R.string.ticket_backlog)
                },
                onPick = { pickingSprint = false; viewModel.setSprint(it) },
                onDismiss = { pickingSprint = false },
            )
        }
    }

    if (editingText && ticket != null) {
        TextDialog(
            title = ticket.title,
            description = ticket.description.orEmpty(),
            busy = state.saving,
            onSave = { title, description -> viewModel.setText(title, description) { if (it) editingText = false } },
            onDismiss = { editingText = false },
        )
    }

    editingComment?.let { comment ->
        var text by rememberSaveable(comment.id) { mutableStateOf(comment.body) }
        AlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text(stringResource(R.string.comment_edit)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(5000) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { viewModel.editComment(comment, text) { if (it) editingComment = null } }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { editingComment = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deletingComment?.let { comment ->
        Confirm(
            title = stringResource(R.string.comment_delete_title),
            text = plainText(comment.body).take(140),
            onConfirm = { deletingComment = null; viewModel.deleteComment(comment) },
            onDismiss = { deletingComment = null },
        )
    }

    deletingFile?.let { file ->
        Confirm(
            title = stringResource(R.string.attachment_delete_title),
            text = file.name,
            onConfirm = { deletingFile = null; viewModel.deleteAttachment(file) },
            onDismiss = { deletingFile = null },
        )
    }
}

@Composable
private fun Confirm(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = CtTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(
    ticket: Ticket,
    user: User,
    changing: Boolean,
    saving: Boolean,
    statuses: List<Status>,
    onOpenStatuses: () -> Unit,
    onStatus: (Status) -> Unit,
    onPriority: (String) -> Unit,
    onAssignee: () -> Unit,
    onTake: () -> Unit,
    onDue: () -> Unit,
    onClearDue: () -> Unit,
    onSprint: () -> Unit,
    onParent: (String) -> Unit,
) {
    val colors = CtTheme.colors
    val member = !user.isGuest
    var statusMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    CtCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeIcon(ticket.type, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = member) { priorityMenu = true }.padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityIcon(ticket.priority, size = 20.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(priorityName(ticket.priority), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    if (member) Icon(Icons.Rounded.ArrowDropDown, null, tint = colors.muted, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                    PRIORITIES.reversed().forEach { priority ->
                        DropdownMenuItem(
                            text = { Text(priorityName(priority)) },
                            leadingIcon = { PriorityIcon(priority) },
                            trailingIcon = { if (priority == ticket.priority) Icon(Icons.Rounded.Check, null) },
                            onClick = { priorityMenu = false; if (priority != ticket.priority) onPriority(priority) },
                        )
                    }
                }
            }
            if (saving) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.weight(1f))
            Box {
                Surface(
                    onClick = { onOpenStatuses(); statusMenu = true },
                    enabled = member,
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                ) {
                    if (changing) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
                    } else {
                        StatusChip(ticket.status, trailing = if (member) Icons.Rounded.ArrowDropDown else null)
                    }
                }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    if (statuses.isEmpty()) {
                        DropdownMenuItem(text = { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }, onClick = {}, enabled = false)
                    }
                    statuses.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.name) },
                            leadingIcon = {
                                if (status.name == ticket.status.name) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp))
                            },
                            onClick = { statusMenu = false; onStatus(status) },
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
        Spacer(Modifier.height(8.dp))
        Row {
            Fact(
                stringResource(R.string.ticket_assignee),
                ticket.assignee?.name ?: stringResource(R.string.ticket_unassigned),
                Modifier.weight(1f),
                onClick = if (member) onAssignee else null,
            )
            val late = ticket.dueOn != null && !ticket.isDone && ticket.dueOn < LocalDate.now().toString()
            Fact(
                stringResource(R.string.ticket_due),
                ticket.dueOn?.let(::shortDate) ?: "—",
                Modifier.weight(1f),
                onClick = if (member) onDue else null,
                valueColor = if (late) colors.danger else null,
                onClear = if (member && ticket.dueOn != null) onClearDue else null,
            )
        }
        if (member && ticket.assignee?.id != user.id && !ticket.isDone) {
            TextButton(onClick = onTake, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Rounded.PanTool, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ticket_take))
            }
        }
        Row {
            Fact(
                stringResource(R.string.ticket_sprint),
                ticket.sprint ?: stringResource(R.string.ticket_backlog),
                Modifier.weight(1f),
                onClick = if (member) onSprint else null,
            )
            ticket.parent?.let { parent ->
                Fact(stringResource(R.string.ticket_parent), parent, Modifier.weight(1f), onClick = { onParent(parent) }, valueColor = colors.primary)
            } ?: Spacer(Modifier.weight(1f))
        }
        Row {
            Fact(stringResource(R.string.ticket_estimate), ticket.estimateMinutes?.let { minutesText(it) } ?: "—", Modifier.weight(1f))
            Fact(stringResource(R.string.ticket_logged_label), minutesText(ticket.loggedMinutes), Modifier.weight(1f))
            Fact(stringResource(R.string.ticket_remaining_label), ticket.remainingMinutes?.let { minutesText(it) } ?: "—", Modifier.weight(1f))
        }
    }
}

/** One fact of the ticket; one that can be changed is a button. */
@Composable
private fun Fact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    valueColor: Color? = null,
    onClear: (() -> Unit)? = null,
) {
    val colors = CtTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor ?: colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onClick != null && onClear == null) Icon(Icons.Rounded.ArrowDropDown, null, tint = colors.muted, modifier = Modifier.size(18.dp))
            }
        }
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.clear), tint = colors.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SmallTicketRow(key: String, title: String, status: Status, type: String?, lead: String? = null, onClick: () -> Unit) {
    val colors = CtTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (type != null) {
            TypeIcon(type, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                lead?.let {
                    Text("$it ", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                Text(key, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (status.category == "done") colors.muted else colors.text,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(status)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Attachments(
    attachments: List<Attachment>,
    uploading: Boolean,
    opening: Int?,
    thumbnail: suspend (Attachment) -> android.graphics.Bitmap?,
    onOpen: (Attachment) -> Unit,
    onDelete: (Attachment) -> Unit,
    onAdd: net.levente.cantotrack.mobile.ui.components.PhotoSource,
) {
    val colors = CtTheme.colors
    var adding by remember { mutableStateOf(false) }
    CtCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.ticket_attachments, attachments.size), Modifier.weight(1f))
            if (uploading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                Box {
                    IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.AddAPhoto, stringResource(R.string.attachment_add), tint = colors.primary) }
                    DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.attachment_camera)) }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = { adding = false; onAdd.camera() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.attachment_gallery)) }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) }, onClick = { adding = false; onAdd.gallery() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.attachment_file)) }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) }, onClick = { adding = false; onAdd.file() })
                    }
                }
            }
        }
        if (attachments.isEmpty()) {
            Text(
                stringResource(R.string.attachment_none),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attachments, key = { it.id }) { attachment ->
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.slateSoft)
                            .combinedClickable(onClick = { onOpen(attachment) }, onLongClick = { onDelete(attachment) }),
                    ) {
                        val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.id) {
                            if (attachment.isImage) value = thumbnail(attachment)
                        }
                        val image = bitmap
                        if (image != null) {
                            Image(image.asImageBitmap(), contentDescription = attachment.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = colors.muted)
                                Spacer(Modifier.height(4.dp))
                                Text(attachment.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (opening == attachment.id) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.attachment_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/** Who worked on it and how long: a line a person, and the latest entries. */
@Composable
private fun Hours(worklogs: List<Worklog>) {
    val colors = CtTheme.colors
    var all by rememberSaveable { mutableStateOf(false) }
    CtCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.ticket_hours, minutesText(worklogs.sumOf { it.minutes })))
        Spacer(Modifier.height(8.dp))
        worklogs.groupBy { it.user.id }.values.sortedByDescending { list -> list.sumOf { it.minutes } }.forEach { list ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(list.first().user.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(minutesText(list.sumOf { it.minutes }), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(4.dp))
        (if (all) worklogs else worklogs.take(4)).forEach { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(shortDate(entry.date), entry.user.name, entry.workType).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                    entry.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                Text(minutesText(entry.minutes), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (worklogs.size > 4) {
            TextButton(onClick = { all = !all }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(if (all) R.string.show_less else R.string.show_all, worklogs.size))
            }
        }
    }
}

@Composable
private fun CommentCard(comment: Comment, mine: Boolean, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val colors = CtTheme.colors
    var menu by remember { mutableStateOf(false) }
    CtCard(Modifier.fillMaxWidth(), border = if (mine) colors.primary.copy(alpha = 0.3f) else colors.border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(comment.author.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    dateTimeText(comment.createdAt) + if (comment.editedAt != null) " · " + stringResource(R.string.comment_edited) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            if (onEdit != null || onDelete != null) {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.more), tint = colors.muted)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        onEdit?.let {
                            DropdownMenuItem(text = { Text(stringResource(R.string.comment_edit)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; it() })
                        }
                        onDelete?.let {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = colors.danger) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = colors.danger) },
                                onClick = { menu = false; it() },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(plainText(comment.body), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The title and the text, corrected. */
@Composable
private fun TextDialog(title: String, description: String, busy: Boolean, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var newTitle by rememberSaveable { mutableStateOf(title) }
    var newDescription by rememberSaveable { mutableStateOf(description) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ticket_edit_text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it.take(250) },
                    label = { Text(stringResource(R.string.new_ticket_title)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newDescription,
                    onValueChange = { newDescription = it.take(20000) },
                    label = { Text(stringResource(R.string.new_ticket_description)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 5,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.ticket_markdown_hint), style = MaterialTheme.typography.bodySmall, color = CtTheme.colors.muted)
            }
        },
        confirmButton = {
            TextButton(enabled = newTitle.isNotBlank() && !busy, onClick = { onSave(newTitle, newDescription) }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
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
