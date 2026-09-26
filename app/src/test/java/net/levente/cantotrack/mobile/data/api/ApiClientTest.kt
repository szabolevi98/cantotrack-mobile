package net.levente.cantotrack.mobile.data.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The client against a fake server that answers the way CantoTrack's API does. */
class ApiClientTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var api: ApiClient
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server.start()
        baseUrl = server.url("/cantotrack/web").toString().trimEnd('/')
        api = ApiClient(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build(), json)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())
    }

    private val connection get() = Connection(baseUrl, "ct_token")

    @Test
    fun `signing in posts the email, the password and the device, and returns the token`() = runTest {
        respond(201, """{"data":{"token":"ct_abc","user":{"id":7,"name":"Anna Kovács","handle":"anna","email":"anna@example.test","role":"member"}}}""")

        val result = api.login(baseUrl, "anna@example.test", "secret", "Pixel 8")

        assertEquals("ct_abc", result.token)
        assertEquals("AK", result.user.initials)
        val request = server.takeRequest()
        assertEquals("/cantotrack/web/api/v1/auth/login", request.url.encodedPath)
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"email\":\"anna@example.test\""))
        assertTrue(body.contains("\"device\":\"Pixel 8\""))
        assertFalse("No code was asked for, so none is sent.", body.contains("\"code\""))
    }

    @Test
    fun `a wrong password is an Http 401, not a lost session`() = runTest {
        respond(401, """{"error":{"status":401,"message":"That email address and password do not match an account."}}""")

        try {
            api.login(baseUrl, "anna@example.test", "wrong", "Pixel 8")
            fail("Expected an error")
        } catch (e: ApiException.Http) {
            assertEquals(401, e.status)
            assertFalse(e.twoFactorRequired)
        }
    }

    @Test
    fun `two-step sign-in asks for the code, and the code goes in the same request`() = runTest {
        respond(403, """{"error":{"status":403,"message":"Two-step sign-in is on.","details":{"two_factor_required":true}}}""")
        respond(201, """{"data":{"token":"ct_def","user":{"id":7,"name":"Anna","email":"a@b","role":"member"}}}""")

        try {
            api.login(baseUrl, "a@b", "secret", "Pixel 8")
            fail("Expected the server to ask for a code")
        } catch (e: ApiException.Http) {
            assertEquals(403, e.status)
            assertTrue(e.twoFactorRequired)
        }
        server.takeRequest()

        assertEquals("ct_def", api.login(baseUrl, "a@b", "secret", "Pixel 8", "123 456").token)
        assertTrue(server.takeRequest().body!!.utf8().contains("\"code\":\"123 456\""))
    }

    @Test
    fun `a refused token elsewhere is Unauthorized, so the app signs out`() = runTest {
        respond(401, """{"error":{"status":401,"message":"That token is not valid."}}""")

        try {
            api.me(connection)
            fail("Expected an error")
        } catch (e: ApiException.Unauthorized) {
            assertEquals("Bearer ct_token", server.takeRequest().headers["Authorization"])
        }
    }

    @Test
    fun `the server's sentence comes through as the message`() = runTest {
        respond(422, """{"error":{"status":422,"message":"CT-4 cannot go from Done to Backlog."}}""")

        try {
            api.changeStatus(connection, "CT-4", "Backlog", 3)
            fail("Expected an error")
        } catch (e: ApiException.Http) {
            assertEquals("CT-4 cannot go from Done to Backlog.", e.message)
        }
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertTrue(request.body!!.utf8().contains("\"version\":3"))
    }

    @Test
    fun `a page that is not the API is a bad response`() = runTest {
        respond(502, "<html>Bad gateway</html>")

        try {
            api.ticket(connection, "CT-1")
            fail("Expected an error")
        } catch (e: ApiException.BadResponse) {
            // The address points at something else.
        }
    }

    @Test
    fun `my open tickets ask for open, me and the search`() = runTest {
        respond(200, """{"data":[{"key":"CT-1","project":"CT","title":"Board","status":{"id":1,"name":"To do","category":"todo"}}],"meta":{"page":1,"per_page":50,"total":1,"pages":1}}""")

        val page = api.tickets(connection, " board ", mineOnly = true)

        assertEquals("CT-1", page.data.single().key)
        val url = server.takeRequest().url
        assertEquals("1", url.queryParameter("open"))
        assertEquals("me", url.queryParameter("assignee"))
        assertEquals("board", url.queryParameter("q"))
    }

    @Test
    fun `searching every ticket leaves out the open filter`() = runTest {
        respond(200, """{"data":[],"meta":{"page":1,"per_page":50,"total":0,"pages":1}}""")

        api.tickets(connection, "board", mineOnly = false, openOnly = false)

        val url = server.takeRequest().url
        assertNull(url.queryParameter("open"))
        assertNull(url.queryParameter("assignee"))
    }

    @Test
    fun `an entry is changed with a PATCH of its time, day and note`() = runTest {
        respond(200, """{"data":{"id":42,"ticket":"CT-12","user":{"id":7,"name":"Anna"},"date":"2026-09-10","minutes":90,"note":"Fixed"}}""")

        val entry = api.updateWorklog(connection, 42, "1h 30m", "2026-09-10", " Fixed ")

        assertEquals(90, entry.minutes)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/cantotrack/web/api/v1/worklogs/42", request.url.encodedPath)
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"time\":\"1h 30m\""))
        assertTrue(body.contains("\"date\":\"2026-09-10\""))
        assertTrue(body.contains("\"note\":\"Fixed\""))
    }

    @Test
    fun `no clock is null, and a running one says how long it has run`() = runTest {
        respond(200, """{"data":null}""")
        respond(200, """{"data":{"ticket":"CT-1","title":"Board","started_at":"2026-09-26T13:38:27+02:00","seconds":95}}""")

        assertNull(api.timer(connection))
        val timer = api.timer(connection)!!
        assertEquals("CT-1", timer.ticket)
        assertEquals(95L, timer.seconds)
    }

    @Test
    fun `stopping under a minute logs nothing, and discarding answers 204`() = runTest {
        respond(200, """{"data":null}""")
        respond(200, """{"data":{"minutes":15,"ticket":"CT-1"}}""")
        respond(204)

        assertNull(api.stopTimer(connection, null))
        assertEquals(15, api.stopTimer(connection, "Done")!!.minutes)
        api.discardTimer(connection)

        server.takeRequest()
        assertTrue(server.takeRequest().body!!.utf8().contains("\"note\":\"Done\""))
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `server addresses are tidied the way people type them`() {
        assertEquals("https://tracker.example.com", ApiClient.normalizeBaseUrl("tracker.example.com/"))
        assertEquals("https://tracker.example.com", ApiClient.normalizeBaseUrl("https://tracker.example.com/api/v1"))
        assertEquals("http://10.0.2.2/cantotrack/web", ApiClient.normalizeBaseUrl(" http://10.0.2.2/cantotrack/web "))
        assertNull(ApiClient.normalizeBaseUrl(""))
        assertNull(ApiClient.normalizeBaseUrl("ftp://tracker.example.com"))
    }
}
