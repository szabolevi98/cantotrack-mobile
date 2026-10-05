package net.levente.cantotrack.mobile.data.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
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
    fun `a change sent again after a lost answer carries the same key, and a new change a new one`() = runTest {
        val entry = """{"data":{"id":42,"ticket":"CT-12","user":{"id":7,"name":"Anna"},"date":"2026-09-10","minutes":45}}"""
        // The first answer comes too late: the phone gives up, but the server may have logged it.
        server.enqueue(MockResponse.Builder().code(201).body(entry).headersDelay(3, TimeUnit.SECONDS).build())
        respond(201, entry)
        respond(201, entry)

        try {
            api.logWork(connection, "CT-12", "45m", "2026-09-10", "")
            fail("Expected the answer to be lost")
        } catch (e: ApiException.Network) {
            // The person taps again.
        }
        api.logWork(connection, "CT-12", "45m", "2026-09-10", "")
        // Answered, so logging 45 minutes again is meant: a new change.
        api.logWork(connection, "CT-12", "45m", "2026-09-10", "")

        val first = server.takeRequest().headers["Idempotency-Key"]
        val resent = server.takeRequest().headers["Idempotency-Key"]
        val another = server.takeRequest().headers["Idempotency-Key"]
        assertTrue(!first.isNullOrBlank())
        assertEquals(first, resent)
        assertTrue(another != first)
    }

    @Test
    fun `reading and signing in carry no key`() = runTest {
        respond(200, """{"data":null}""")
        respond(201, """{"data":{"token":"ct_abc","user":{"id":7,"name":"Anna","email":"a@b","role":"member"}}}""")

        api.timer(connection)
        api.login(baseUrl, "a@b", "secret", "Pixel 8")

        assertNull(server.takeRequest().headers["Idempotency-Key"])
        assertNull(server.takeRequest().headers["Idempotency-Key"])
    }

    @Test
    fun `a key held longer than half a day is not reused`() {
        var time = 0L
        val keys = ResendKeys { time }

        val first = keys.keyFor("POST /x\n{}")
        time += ResendKeys.KEEP_MILLIS + 1

        assertTrue(keys.keyFor("POST /x\n{}") != first)
    }

    @Test
    fun `the quick filters become the server's own parameters`() = runTest {
        respond(200, """{"data":[],"meta":{"page":1,"per_page":100,"total":0,"pages":1}}""")

        api.tickets(
            connection,
            TicketFilter(due = "overdue", type = "bug", project = "CT", query = "priority = high", sprint = 12, topLevel = true),
            page = 2,
            perPage = 100,
        )

        val url = server.takeRequest().url
        assertEquals("overdue", url.queryParameter("due"))
        assertEquals("bug", url.queryParameter("type"))
        assertEquals("CT", url.queryParameter("project"))
        assertEquals("priority = high", url.queryParameter("query"))
        assertEquals("12", url.queryParameter("sprint"))
        assertEquals("1", url.queryParameter("top_level"))
        assertEquals("1", url.queryParameter("open"))
        assertNull("Not mine only unless asked.", url.queryParameter("assignee"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("100", url.queryParameter("per_page"))
    }

    @Test
    fun `a change to a ticket sends only its fields and the version read`() = runTest {
        respond(200, """{"data":{"key":"CT-4","project":"CT","title":"Board","status":{"id":1,"name":"To do","category":"todo"},"version":8,"starred":true,"watching":false}}""")

        val ticket = api.updateTicket(connection, "CT-4", kotlinx.serialization.json.buildJsonObject { put("assignee_id", null as Int?) }, 7)

        assertEquals(8, ticket.version)
        assertEquals(true, ticket.starred)
        val body = server.takeRequest().body!!.utf8()
        assertEquals("""{"assignee_id":null,"version":7}""", body)
    }

    @Test
    fun `starring is a POST and taking the star off a DELETE`() = runTest {
        respond(204)
        respond(204)

        api.star(connection, "CT-4", true)
        api.star(connection, "CT-4", false)

        val starred = server.takeRequest()
        assertEquals("POST", starred.method)
        assertEquals("/cantotrack/web/api/v1/tickets/CT-4/star", starred.url.encodedPath)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `the same photo sent again after a lost answer is the same request`() = runTest {
        val uploaded = """{"data":[{"id":5,"name":"a.jpg","type":"image/jpeg","size":3}],"errors":[]}"""
        server.enqueue(MockResponse.Builder().code(201).body(uploaded).headersDelay(3, TimeUnit.SECONDS).build())
        respond(201, uploaded)
        val photo = byteArrayOf(1, 2, 3)

        try {
            api.upload(connection, "CT-4", "a.jpg", "image/jpeg", photo)
            fail("Expected the answer to be lost")
        } catch (e: ApiException.Network) {
            // The person taps again.
        }
        val result = api.upload(connection, "CT-4", "a.jpg", "image/jpeg", photo)

        assertEquals("a.jpg", result.data.single().name)
        val first = server.takeRequest()
        val again = server.takeRequest()
        assertEquals(first.headers["Idempotency-Key"], again.headers["Idempotency-Key"])
        assertEquals(first.headers["Content-Type"], again.headers["Content-Type"])
        assertTrue(first.headers["Content-Type"]!!.startsWith("multipart/form-data"))
    }

    @Test
    fun `the week reads each day against what it asks for, and where it stands`() = runTest {
        respond(
            200,
            """{"data":{"monday":"2026-09-21","sunday":"2026-09-27","user":{"id":3,"name":"Anna"},
               "days":[{"date":"2026-09-21","expected_minutes":480,"logged_minutes":465,"holiday":null,"absence":null},
                       {"date":"2026-09-25","expected_minutes":0,"logged_minutes":0,"holiday":null,"absence":"vacation"}],
               "logged_minutes":465,"expected_minutes":1920,"expected_to_date_minutes":1920,
               "state":{"state":"rejected","comment":"Friday is missing.","reviewer":{"id":1,"name":"Levente"}},
               "can_submit":true,"locked_until":null,"absences":[{"id":9,"starts_on":"2026-09-25","ends_on":"2026-09-25","kind":"vacation"}]}}""",
        )

        val week = api.week(connection, "2026-09-23")

        assertEquals("2026-09-23", server.takeRequest().url.queryParameter("week"))
        assertEquals(1920, week.expectedMinutes)
        assertEquals("vacation", week.days[1].absence)
        assertEquals("rejected", week.state!!.state)
        assertEquals("Friday is missing.", week.state!!.comment)
        assertTrue(week.canSubmit)
        assertEquals(9, week.absences.single().id)
    }

    @Test
    fun `the bell says how many are unread`() = runTest {
        respond(
            200,
            """{"data":[{"id":130,"kind":"mentioned","reason":"mentioned","read":false,"text":"Anna mentioned you",
               "actor":{"id":3,"name":"Anna"},"ticket":{"key":"CT-14","title":"Export"},"epic":null,"project":"CT",
               "created_at":"2026-09-22 14:05:11","url":"https://x/t/CT-14"}],
               "meta":{"page":1,"per_page":1,"total":4,"pages":4,"unread":4}}""",
        )

        val page = api.notifications(connection, unreadOnly = true, perPage = 1)

        assertEquals(4, page.meta.unread)
        assertEquals("CT-14", page.data.single().ticket!!.key)
        assertEquals("1", server.takeRequest().url.queryParameter("unread"))
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
