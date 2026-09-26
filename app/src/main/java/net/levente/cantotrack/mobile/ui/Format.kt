package net.levente.cantotrack.mobile.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** Text a ViewModel hands to the UI without holding a Context: a resource, or a sentence from the server. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Res -> stringResource(id, *args.map { if (it is Minutes) minutesText(it.value) else it }.toTypedArray())
        is Raw -> text
    }
}

/** An argument of [UiText.Res] that is a length of time, written out when the text is. */
data class Minutes(val value: Int)

/**
 * The sentence for a failed request. CantoTrack writes its errors for people,
 * in their own language, so those are shown as they come.
 */
fun ApiException.toUiText(): UiText = when (this) {
    is ApiException.Network -> UiText.Res(R.string.error_network)
    is ApiException.Unauthorized -> UiText.Res(R.string.error_session_expired)
    is ApiException.BadResponse -> UiText.Res(R.string.error_bad_response)
    is ApiException.Http -> message?.takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: UiText.Res(R.string.error_server, listOf(status))
}

/** 90 as "1 h 30 m" (or "1 ó 30 p"): the way the web writes hours. */
fun formatMinutes(context: Context, minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> context.getString(R.string.duration_m, m)
        m == 0 -> context.getString(R.string.duration_h, h)
        else -> context.getString(R.string.duration_hm, h, m)
    }
}

@Composable
fun minutesText(minutes: Int): String = formatMinutes(LocalContext.current, minutes)

/** A running clock: 0:05:09. The hours are always there, so 5:09 is never read as five hours. */
fun clockText(seconds: Long): String = "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)

/** The server's "2026-09-26 12:10:29" in the phone's own way, or as it came. */
fun dateTimeText(value: String): String = try {
    LocalDateTime.parse(value.replace(' ', 'T')).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
} catch (e: DateTimeParseException) {
    value
}

fun dayText(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

/** The worklog body the web takes: "1h 30m". */
fun timeInput(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
