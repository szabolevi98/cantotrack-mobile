package net.levente.cantotrack.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.ui.minutesText
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val QUICK = listOf(15, 30, 60, 120)

/**
 * Hours by hand: how long, which day, and a note — the web's "Log time" on a
 * phone. The same dialog makes an entry and corrects one; [header] is what
 * the hours go on (a ticket picked in a search, or the ticket already known),
 * and [extra] holds what else can be done with an entry (delete it).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorklogDialog(
    title: String,
    confirmLabel: String,
    busy: Boolean,
    onConfirm: (minutes: Int, date: String, note: String) -> Unit,
    onDismiss: () -> Unit,
    initialMinutes: Int = 30,
    initialDate: LocalDate = LocalDate.now(),
    initialNote: String = "",
    canConfirm: Boolean = true,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var minutes by rememberSaveable { mutableIntStateOf(initialMinutes) }
    var date by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val day = LocalDate.parse(date)
    val today = LocalDate.now()

    if (picking) {
        DayPickerDialog(day, onPick = { date = it.toString(); picking = false }, onDismiss = { picking = false })
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header?.invoke(this)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FilledTonalIconButton(onClick = { minutes = (minutes - 15).coerceAtLeast(15) }) {
                        Icon(Icons.Rounded.Remove, stringResource(R.string.worklog_less))
                    }
                    Text(
                        minutesText(minutes),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(onClick = { minutes = (minutes + 15).coerceAtMost(24 * 60) }) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.worklog_more))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK.forEach { quick ->
                        FilterChip(selected = minutes == quick, onClick = { minutes = quick }, label = { Text(minutesText(quick)) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = day == today, onClick = { date = today.toString() }, label = { Text(stringResource(R.string.worklog_today)) })
                    FilterChip(
                        selected = day == today.minusDays(1),
                        onClick = { date = today.minusDays(1).toString() },
                        label = { Text(stringResource(R.string.worklog_yesterday)) },
                    )
                    FilterChip(
                        selected = day.isBefore(today.minusDays(1)),
                        onClick = { picking = true },
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
                        label = {
                            Text(
                                if (day.isBefore(today.minusDays(1))) day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                                else stringResource(R.string.worklog_other_day),
                            )
                        },
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(stringResource(R.string.worklog_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                extra?.invoke(this)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes, date, note) }, enabled = !busy && canConfirm) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A day up to today, from the calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)

            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(picker) }
}

/**
 * Which ticket the hours go on: one's own open tickets to start with, and
 * every ticket (closed ones too) matching what is typed.
 */
@Composable
fun TicketPicker(selected: Ticket?, onSelect: (Ticket?) -> Unit, search: suspend (String) -> List<Ticket>) {
    val colors = CtTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    if (selected != null) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeIcon(selected.type)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(selected.key, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Text(selected.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onSelect(null) }) { Icon(Icons.Rounded.Close, stringResource(R.string.worklog_other_ticket)) }
        }
        HorizontalDivider(color = colors.border)
        return
    }

    LaunchedEffect(query) {
        delay(if (query.isEmpty()) 0 else 300)
        loading = true
        failed = false
        results = try {
            search(query)
        } catch (e: ApiException) {
            failed = true
            emptyList()
        }
        loading = false
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.worklog_ticket_search)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(if (query.isBlank()) R.string.worklog_ticket_mine else R.string.worklog_ticket_found),
        style = MaterialTheme.typography.bodySmall,
        color = colors.muted,
    )
    Box(Modifier.fillMaxWidth()) {
        Column {
            results.take(6).forEach { ticket ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(ticket) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeIcon(ticket.type)
                    Spacer(Modifier.width(6.dp))
                    Text(ticket.key, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(ticket.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            if (!loading && results.isEmpty()) {
                Text(
                    stringResource(if (failed) R.string.error_network else R.string.tickets_nothing_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.TopEnd), strokeWidth = 2.dp)
    }
}
