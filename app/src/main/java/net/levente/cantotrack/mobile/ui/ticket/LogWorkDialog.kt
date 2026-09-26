package net.levente.cantotrack.mobile.ui.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.ui.minutesText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val QUICK = listOf(15, 30, 60, 120)

/** Hours by hand: how long, which day, and a note. The web's "Log time", on a phone. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogWorkDialog(busy: Boolean, onLog: (minutes: Int, date: String, note: String) -> Unit, onDismiss: () -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var note by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    val day = LocalDate.parse(date)
    val today = LocalDate.now()

    if (picking) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(picker) }
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.worklog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onLog(minutes, date, note) }, enabled = !busy) { Text(stringResource(R.string.worklog_log)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}
