package net.levente.cantotrack.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.timer.RunningClock
import net.levente.cantotrack.mobile.ui.clockText
import net.levente.cantotrack.mobile.ui.theme.CtTheme

/** How long the clock has run, counted on every second while it is on screen. */
@Composable
fun rememberClockSeconds(clock: RunningClock): Long {
    var seconds by remember(clock) { mutableLongStateOf(clock.seconds()) }
    LaunchedEffect(clock) {
        while (true) {
            seconds = clock.seconds()
            delay(1000 - (System.currentTimeMillis() % 1000))
        }
    }
    return seconds
}

/** The running clock at the top of a screen: the ticket, the time, and a stop button. */
@Composable
fun ClockCard(clock: RunningClock, onOpen: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CtTheme.colors
    val seconds = rememberClockSeconds(clock)
    CtCard(modifier = modifier.fillMaxWidth(), onClick = onOpen, border = colors.primary.copy(alpha = 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colors.success))
            Spacer(Modifier.width(8.dp))
            SectionLabel(stringResource(R.string.clock_running))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(clock.ticket, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Text(clock.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                clockText(seconds),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 24.sp),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        CtButton(
            stringResource(R.string.timer_stop_and_log),
            onClick = onStop,
            icon = Icons.Rounded.Stop,
            color = colors.danger,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Asks for a note before the clock stops and becomes a worklog. [onDiscard]
 * stops it without logging anything.
 */
@Composable
fun StopClockDialog(clock: RunningClock, onStop: (String) -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    val seconds = rememberClockSeconds(clock)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.timer_discard_title)) },
            text = { Text(stringResource(R.string.timer_discard_text, clockText(seconds))) },
            confirmButton = {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.timer_discard), color = CtTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_stop_title, clock.ticket)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.timer_stop_text, clockText(seconds)))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(stringResource(R.string.worklog_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.timer_discard_instead), color = CtTheme.colors.danger)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStop(note) }) { Text(stringResource(R.string.timer_stop_and_log)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
