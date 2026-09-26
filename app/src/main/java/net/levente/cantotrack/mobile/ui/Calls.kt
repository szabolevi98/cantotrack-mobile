package net.levente.cantotrack.mobile.ui

import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Connection
import net.levente.cantotrack.mobile.data.session.SessionManager

/**
 * Runs a request as the signed-in person. A token the server no longer takes
 * signs the app out, whichever screen asked; the exception still goes on, so
 * the screen can stop its spinner.
 */
suspend fun <T> SessionManager.call(block: suspend (Connection) -> T): T {
    val session = current ?: throw ApiException.Unauthorized("")
    try {
        return block(session.connection())
    } catch (e: ApiException.Unauthorized) {
        expire()
        throw e
    }
}
