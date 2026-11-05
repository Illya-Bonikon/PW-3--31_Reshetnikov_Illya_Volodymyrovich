package ua.kpi.building_audit

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ua.kpi.building_audit.error.AppError
import ua.kpi.building_audit.error.AuthFailure
import ua.kpi.building_audit.error.ErrorCenter
import ua.kpi.building_audit.error.ErrorJournal
import ua.kpi.building_audit.error.ErrorMapper
import ua.kpi.building_audit.error.JournalEntry
import ua.kpi.building_audit.error.Outcome
import ua.kpi.building_audit.error.RetryPolicy
import ua.kpi.building_audit.network.ConnectivityStatus
import ua.kpi.building_audit.network.NetworkState
import ua.kpi.building_audit.network.RetrofitFactory

private class MemoryJournal : ErrorJournal {
    val entries = mutableListOf<JournalEntry>()
    override suspend fun record(entry: JournalEntry) { entries += entry }
    override suspend fun recent(limit: Int) = entries.takeLast(limit).asReversed()
    override suspend fun clear() = entries.clear()
}

private class FakeConnectivity(online: Boolean = true) : ConnectivityStatus {
    override val state = MutableStateFlow(NetworkState(online, "test"))
}

/** Повний ланцюжок Retrofit → ErrorMapper → ErrorCenter проти MockWebServer. */
class ErrorHandlingTest {
    private val server = MockWebServer()
    private val gson = Gson()
    private val journal = MemoryJournal()
    private val connectivity = FakeConnectivity()
    private val pauses = mutableListOf<Long>()
    private val errors = ErrorCenter(ErrorMapper(gson), journal, connectivity, clock = { 0L }, sleep = { pauses += it })
    private val policy = RetryPolicy(maxAttempts = 3, initialDelayMs = 100)

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun api() = RetrofitFactory.create(server.url("/api/v1/").toString(), gson, connectivity, { "token" }, logBodies = false)

    private fun error(code: Int, serverCode: String, retryAfter: String? = null) = MockResponse()
        .setResponseCode(code)
        .setBody("""{"error":{"code":"$serverCode","message":"test"}}""")
        .apply { if (retryAfter != null) setHeader("Retry-After", retryAfter) }

    private val tariffsJson = """{"heatPriceUahPerGcal":1654.06,"validFrom":"2026-10-01",
        "climate":{"city":"Київ","indoorTempC":20,"outdoorDesignTempC":-15,"heatingDegreeDays":3500},"measures":[]}"""

    @Test
    fun retriesTransientErrorsAndHonoursRetryAfter() = runTest {
        server.enqueue(error(503, "SERVICE_UNAVAILABLE", retryAfter = "2"))
        server.enqueue(error(429, "RATE_LIMITED"))
        server.enqueue(MockResponse().setBody(tariffsJson))

        val result = errors.execute("test", policy) { api().tariffs() }

        assertTrue(result is Outcome.Success)
        // Перша пауза з Retry-After (2 с), друга — експоненційна: 100 · 2^1.
        assertEquals(listOf(2_000L, 200L), pauses)
        assertEquals(2, journal.entries.size)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun serverFailureIsNotRetried() = runTest {
        server.enqueue(error(500, "INTERNAL_ERROR"))
        val result = errors.execute("test", policy) { api().tariffs() }
        assertEquals(AppError.ServerFailure(500), (result as Outcome.Failure).error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun expiredTokenIsClassifiedByServerCode() = runTest {
        server.enqueue(error(401, "TOKEN_EXPIRED"))
        val result = errors.execute("test", guardsSession = false) { api().me() }
        assertEquals(AppError.Unauthorized(AuthFailure.TOKEN_EXPIRED), (result as Outcome.Failure).error)
        assertEquals("Bearer token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun malformedJsonBecomesInvalidResponse() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id": 1, "name": "Навчальний корпус"""))
        val result = errors.execute("test", policy) { api().buildings() }
        assertTrue((result as Outcome.Failure).error is AppError.InvalidResponse)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun offlineRequestIsNotSent() = runTest {
        connectivity.state.value = NetworkState.OFFLINE
        val result = errors.execute("test", policy) { api().tariffs() }
        assertEquals(AppError.NoConnection, (result as Outcome.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun retryPolicyStopsAtMaxAttempts() {
        assertTrue(policy.shouldRetry(2, AppError.Timeout))
        assertFalse(policy.shouldRetry(3, AppError.Timeout))
        assertFalse(policy.shouldRetry(1, AppError.Forbidden))
        assertEquals(400L, policy.delayBefore(4, AppError.Timeout))
    }
}
