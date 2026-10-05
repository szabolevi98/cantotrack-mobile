package net.levente.cantotrack.mobile.ui.hours

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import net.levente.cantotrack.mobile.data.api.Absence
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.User
import net.levente.cantotrack.mobile.data.api.Week
import net.levente.cantotrack.mobile.data.api.Worklog
import net.levente.cantotrack.mobile.data.timer.RunningClock
import net.levente.cantotrack.mobile.ui.ABSENCE_KINDS
import net.levente.cantotrack.mobile.ui.absenceName
import net.levente.cantotrack.mobile.ui.components.ClockCard
import net.levente.cantotrack.mobile.ui.components.CtButton
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.DatePickDialog
import net.levente.cantotrack.mobile.ui.components.DayPickerDialog
import net.levente.cantotrack.mobile.ui.components.EmptyState
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.components.SectionLabel
import net.levente.cantotrack.mobile.ui.components.TicketPicker
import net.levente.cantotrack.mobile.ui.components.WorklogDialog
import net.levente.cantotrack.mobile.ui.dayText
import net.levente.cantotrack.mobile.ui.minutesText
import net.levente.cantotrack.mobile.ui.shortDate
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoursScreen(
    viewModel: HoursViewModel,
    user: User,
    clock: RunningClock?,
    snackbar: SnackbarHostState,
    onTicket: (String) -> Unit,
    onStopClock: () -> Unit,
    onLogged: () -> Unit,
    /** Opened from the app icon's "Log time": the dialog is open at once. */
    startLogging: Boolean = false,
    onStartedLogging: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    val today = LocalDate.now()
    var pickingWeek by rememberSaveable { mutableStateOf(false) }
    // A new entry, on this day; null when none is being made.
    var newOn by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<Worklog?>(null) }
    var deleting by remember { mutableStateOf<Worklog?>(null) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var addingAbsence by rememberSaveable { mutableStateOf(false) }
    var deletingAbsence by remember { mutableStateOf<Absence?>(null) }

    LaunchedEffect(startLogging) {
        if (startLogging && !user.isGuest) {
            newOn = today
            onStartedLogging()
        }
    }

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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val short = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            CtHeader(
                title = stringResource(R.string.hours_title),
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
                // The week itself is the button for a week far back: a calendar opens on it.
                Surface(
                    onClick = { pickingWeek = true },
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.12f),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.hours_week, state.monday.format(short), state.sunday.format(short)),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val week = state.week
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(minutesText(state.total), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (week != null && week.expectedMinutes > 0) {
                            stringResource(R.string.hours_of_expected, minutesText(week.expectedMinutes))
                        } else {
                            stringResource(R.string.hours_this_week_total)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.sidebarText,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    if (!state.isThisWeek) {
                        TextButton(onClick = viewModel::thisWeek) { Text(stringResource(R.string.hours_back_to_this_week), color = Color.White) }
                    }
                }
                if (week != null && week.expectedMinutes > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (state.total.toFloat() / week.expectedMinutes).coerceIn(0f, 1f) },
                        color = if (state.total >= week.expectedMinutes) colors.success else Color.White,
                        trackColor = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    val missing = week.expectedToDateMinutes - state.total
                    if (missing > 0 && !state.monday.isAfter(today)) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(if (state.isThisWeek) R.string.hours_missing_to_date else R.string.hours_missing_week, minutesText(missing)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.sidebarText,
                        )
                    }
                }
            }

            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    // Room at the bottom, so the button does not cover the last day.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (clock != null && state.isThisWeek) {
                        item(key = "clock") { ClockCard(clock, onOpen = { onTicket(clock.ticket) }, onStop = onStopClock) }
                    }
                    state.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                    val week = state.week
                    if (week != null && !user.isGuest && !state.loading) {
                        item(key = "week") {
                            WeekCard(
                                week,
                                submitting = state.submitting,
                                onSubmit = { submitting = true },
                                onAbsence = { addingAbsence = true },
                                onDeleteAbsence = { deletingAbsence = it },
                            )
                        }
                    }
                    when {
                        state.loading -> item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        state.days.isEmpty() && state.error == null -> item(key = "empty") {
                            EmptyState(Icons.Rounded.Schedule, stringResource(R.string.hours_empty_title), stringResource(R.string.hours_empty_text))
                        }
                        else -> items(state.days, key = { it.date.toString() }) { day ->
                            // A handed-in or approved week, and a closed day, take no more hours.
                            val closed = week?.state?.state in setOf("submitted", "approved") ||
                                week?.lockedUntil?.let { day.date.toString() <= it } == true
                            DayCard(
                                day,
                                onAdd = if (user.isGuest || closed) null else ({ newOn = day.date }),
                                onEntry = { if (user.isGuest) onTicket(it.ticket) else editing = it },
                            )
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

        if (!user.isGuest) {
            ExtendedFloatingActionButton(
                onClick = { newOn = if (state.isThisWeek) today else state.monday },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.worklog_title)) },
                containerColor = colors.primary,
                contentColor = if (colors.dark) Color(0xFF0B1628) else Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (pickingWeek) {
        DayPickerDialog(state.monday, onPick = { pickingWeek = false; viewModel.showWeekOf(it) }, onDismiss = { pickingWeek = false })
    }

    newOn?.let { day ->
        var ticket by remember(day) { mutableStateOf<Ticket?>(null) }
        WorklogDialog(
            title = stringResource(R.string.worklog_title),
            confirmLabel = stringResource(R.string.worklog_log),
            busy = state.saving,
            initialDate = day,
            canConfirm = ticket != null,
            workTypes = state.workTypes,
            onConfirm = { minutes, date, note, workType ->
                ticket?.let { viewModel.log(it.key, minutes, date, note, workType) { ok -> if (ok) { newOn = null; onLogged() } } }
            },
            onDismiss = { newOn = null },
            header = { TicketPicker(ticket, onSelect = { ticket = it }, search = viewModel::searchTickets) },
        )
    }

    editing?.let { entry ->
        WorklogDialog(
            title = stringResource(R.string.worklog_edit_title, entry.ticket),
            confirmLabel = stringResource(R.string.save),
            busy = state.saving,
            initialMinutes = entry.minutes,
            initialDate = LocalDate.parse(entry.date),
            initialNote = entry.note.orEmpty(),
            workTypes = state.workTypes,
            initialWorkType = entry.workType,
            onConfirm = { minutes, date, note, workType ->
                viewModel.update(entry, minutes, date, note, workType) { ok -> if (ok) { editing = null; onLogged() } }
            },
            onDismiss = { editing = null },
            extra = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtButton(
                        stringResource(R.string.worklog_open_ticket),
                        onClick = { editing = null; onTicket(entry.ticket) },
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        outlined = true,
                        modifier = Modifier.weight(1f),
                    )
                    CtButton(
                        stringResource(R.string.delete),
                        onClick = { editing = null; deleting = entry },
                        icon = Icons.Rounded.DeleteOutline,
                        color = colors.danger,
                        outlined = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.hours_delete_title)) },
            text = { Text(stringResource(R.string.hours_delete_text, minutesText(entry.minutes), entry.ticket)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entry); deleting = null; onLogged() }) {
                    Text(stringResource(R.string.delete), color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (submitting) {
        val week = state.week
        AlertDialog(
            onDismissRequest = { submitting = false },
            title = { Text(stringResource(R.string.week_submit_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.week_submit_text,
                        minutesText(state.total),
                        minutesText(week?.expectedMinutes ?: 0),
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { submitting = false; viewModel.submit() }) { Text(stringResource(R.string.week_submit)) } },
            dismissButton = { TextButton(onClick = { submitting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (addingAbsence) {
        AbsenceDialog(
            start = if (state.isThisWeek) today else state.monday,
            onSave = { from, to, kind, note, done -> viewModel.addAbsence(from, to, kind, note) { ok -> done(ok); if (ok) addingAbsence = false } },
            onDismiss = { addingAbsence = false },
        )
    }

    deletingAbsence?.let { absence ->
        AlertDialog(
            onDismissRequest = { deletingAbsence = null },
            title = { Text(stringResource(R.string.absence_delete_title)) },
            text = { Text(absenceRange(absence)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAbsence(absence); deletingAbsence = null }) {
                    Text(stringResource(R.string.delete), color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deletingAbsence = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun absenceRange(absence: Absence): String =
    if (absence.startsOn == absence.endsOn) shortDate(absence.startsOn) else "${shortDate(absence.startsOn)} – ${shortDate(absence.endsOn)}"

/** Where the week stands with the one approving it, handing it in, and the days away in it. */
@Composable
private fun WeekCard(week: Week, submitting: Boolean, onSubmit: () -> Unit, onAbsence: () -> Unit, onDeleteAbsence: (Absence) -> Unit) {
    val colors = CtTheme.colors
    CtCard(Modifier.fillMaxWidth()) {
        val state = week.state
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint, text) = when (state?.state) {
                "approved" -> Triple(Icons.Rounded.CheckCircle, colors.success, stringResource(R.string.week_approved))
                "submitted" -> Triple(Icons.Rounded.HourglassTop, colors.warning, stringResource(R.string.week_waiting))
                "rejected" -> Triple(Icons.AutoMirrored.Rounded.Undo, colors.danger, stringResource(R.string.week_rejected))
                else -> Triple(Icons.Rounded.Schedule, colors.muted, stringResource(R.string.week_open))
            }
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.titleSmall)
                state?.reviewer?.let {
                    Text(stringResource(R.string.week_reviewed_by, it.name), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
        }
        state?.comment?.takeIf { it.isNotBlank() && state.state == "rejected" }?.let {
            Spacer(Modifier.height(6.dp))
            Text("„$it”", style = MaterialTheme.typography.bodyMedium, color = colors.danger)
        }
        week.lockedUntil?.let {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.week_locked_until, shortDate(it)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        if (week.absences.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = colors.border)
            Spacer(Modifier.height(6.dp))
            week.absences.forEach { absence ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BeachAccess, null, tint = colors.violet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        absenceName(absence.kind) + ": " + absenceRange(absence) + (absence.note?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onDeleteAbsence(absence) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.delete), tint = colors.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CtButton(
                stringResource(R.string.absence_add),
                onClick = onAbsence,
                icon = Icons.Rounded.BeachAccess,
                outlined = true,
                modifier = Modifier.weight(1f),
            )
            if (week.canSubmit) {
                CtButton(
                    stringResource(R.string.week_submit),
                    onClick = onSubmit,
                    loading = submitting,
                    icon = Icons.AutoMirrored.Rounded.Send,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Days away: from, to, what kind, and a word about it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbsenceDialog(start: LocalDate, onSave: (LocalDate, LocalDate, String, String, (Boolean) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var from by rememberSaveable { mutableStateOf(start.toString()) }
    var to by rememberSaveable { mutableStateOf(start.toString()) }
    var kind by rememberSaveable { mutableStateOf("vacation") }
    var note by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    picking?.let { which ->
        DatePickDialog(
            initial = LocalDate.parse(if (which == "from") from else to),
            notBefore = if (which == "to") LocalDate.parse(from) else null,
            onPick = {
                if (which == "from") {
                    from = it.toString()
                    if (LocalDate.parse(to).isBefore(it)) to = it.toString()
                } else {
                    to = it.toString()
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.absence_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ABSENCE_KINDS.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(absenceName(k)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(stringResource(R.string.absence_from), from, Modifier.weight(1f)) { picking = "from" }
                    DateField(stringResource(R.string.absence_to), to, Modifier.weight(1f)) { picking = "to" }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text(stringResource(R.string.worklog_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.absence_help), style = MaterialTheme.typography.bodySmall, color = CtTheme.colors.muted)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                onSave(LocalDate.parse(from), LocalDate.parse(to), kind, note) { busy = false }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DateField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = CtTheme.colors
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = colors.background, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Text(shortDate(value), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DayCard(day: Day, onAdd: (() -> Unit)?, onEntry: (Worklog) -> Unit) {
    val colors = CtTheme.colors
    val today = day.date == LocalDate.now()
    CtCard(modifier = Modifier.fillMaxWidth(), border = if (today) colors.primary.copy(alpha = 0.4f) else colors.border, contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (today) stringResource(R.string.hours_today) else dayText(day.date),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val why = day.info?.holiday ?: day.info?.absence?.let { absenceName(it) }
                if (why != null) {
                    Text(why, style = MaterialTheme.typography.bodySmall, color = colors.violet)
                }
            }
            val expected = day.expected
            Text(
                when {
                    expected != null && expected > 0 -> stringResource(R.string.hours_day_of, if (day.minutes == 0) "0" else minutesText(day.minutes), minutesText(expected))
                    day.minutes == 0 -> "—"
                    else -> minutesText(day.minutes)
                },
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    day.short -> colors.warning
                    day.minutes == 0 -> colors.muted
                    else -> colors.text
                },
            )
            if (onAdd != null) {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.hours_add_on_day), tint = colors.primary)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
        day.entries.forEach { entry ->
            HorizontalDivider(color = colors.border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEntry(entry) }
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
