package net.levente.cantotrack.mobile.data.api

import java.util.UUID

/**
 * The Idempotency-Key a change is sent with, so that sending it again after a
 * lost connection does not do it twice.
 *
 * On a train the answer to "log 45 minutes" can be lost after the server has
 * done it. The person sees an error and taps again — and without this the 45
 * minutes were there twice. Now the same change (the same method, address and
 * body) sent again before an answer came gets the same key, and CantoTrack
 * answers it with the first answer instead of doing it again.
 *
 * A key is let go once the server has answered, whatever it said: a change
 * made again on purpose after that is a new change, with a new key. It is kept
 * after a lost connection, and after a 409 — the first may still be running.
 * CantoTrack remembers a key for a day; one held longer than half of that is
 * not reused. Servers older than the header simply ignore it.
 */
class ResendKeys(private val now: () -> Long = System::currentTimeMillis) {

    private class Held(val key: String, val since: Long)

    private val held = HashMap<String, Held>()

    /** The key for this change: the one it had if it is being sent again, a new one otherwise. */
    @Synchronized
    fun keyFor(fingerprint: String): String {
        val time = now()
        held.entries.removeAll { time - it.value.since > KEEP_MILLIS }
        return held.getOrPut(fingerprint) { Held(UUID.randomUUID().toString(), time) }.key
    }

    /** The server answered this change: its key is done with. */
    @Synchronized
    fun answered(fingerprint: String) {
        held.remove(fingerprint)
    }

    companion object {
        const val KEEP_MILLIS = 12L * 60 * 60 * 1000
    }
}
