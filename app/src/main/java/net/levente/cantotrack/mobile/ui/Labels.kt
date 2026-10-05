package net.levente.cantotrack.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.levente.cantotrack.mobile.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** The words the server sends as codes, in the phone's language. */

val PRIORITIES = listOf("low", "normal", "high", "urgent")
val TYPES = listOf("task", "bug", "story")
val ABSENCE_KINDS = listOf("vacation", "sick", "other")

@Composable
fun priorityName(priority: String): String = stringResource(
    when (priority) {
        "urgent" -> R.string.priority_urgent
        "high" -> R.string.priority_high
        "low" -> R.string.priority_low
        else -> R.string.priority_medium
    },
)

@Composable
fun typeName(type: String): String = stringResource(
    when (type) {
        "bug" -> R.string.type_bug
        "story" -> R.string.type_story
        else -> R.string.type_task
    },
)

@Composable
fun absenceName(kind: String): String = stringResource(
    when (kind) {
        "sick" -> R.string.absence_sick
        "other" -> R.string.absence_other
        else -> R.string.absence_vacation
    },
)

@Composable
fun linkName(kind: String): String = stringResource(
    when (kind) {
        "blocks" -> R.string.link_blocks
        "blocked_by" -> R.string.link_blocked_by
        "duplicates" -> R.string.link_duplicates
        "duplicated_by" -> R.string.link_duplicated_by
        else -> R.string.link_relates
    },
)

/** 2026-10-01 in the phone's own short way: "2026. okt. 1." */
fun shortDate(value: String): String = try {
    LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (e: DateTimeParseException) {
    value
}
