package net.levente.cantotrack.mobile.ui

import net.levente.cantotrack.mobile.ui.ticket.plainText
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `time goes to the server the way the web writes it`() {
        assertEquals("15m", timeInput(15))
        assertEquals("1h", timeInput(60))
        assertEquals("1h 30m", timeInput(90))
        assertEquals("8h", timeInput(480))
    }

    @Test
    fun `the clock always shows its hours`() {
        assertEquals("0:00:00", clockText(0))
        assertEquals("0:01:17", clockText(77))
        assertEquals("2:05:09", clockText(2 * 3600 + 5 * 60 + 9))
    }

    @Test
    fun `markdown reads as plain text`() {
        assertEquals(
            "Five columns.\n\n☑ columns\n☐ export\n• a note",
            plainText("## Five columns.\n\n- [x] columns\n- [ ] export\n- a **note**"),
        )
    }
}
