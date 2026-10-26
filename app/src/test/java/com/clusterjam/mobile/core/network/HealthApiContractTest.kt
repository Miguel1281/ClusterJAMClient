package com.clusterjam.mobile.core.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HealthApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HealthApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = createRetrofit(server.url("/").toString()).create(HealthApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun healthChecksUseUnprefixedRoutesWithoutSession() = runBlocking {
        enqueue(200, """{"status":"ok"}""")
        enqueue(200, """{"status":"ok"}""")

        assertEquals("ok", api.liveness().status)
        assertEquals("ok", api.readiness().body()?.status)
        assertRequest("/health/live")
        assertRequest("/health/ready")
    }

    @Test
    fun readiness503RemainsAvailableToIndependentCaller() = runBlocking {
        enqueue(200, """{"status":"ok"}""")
        enqueue(503, """{"code":"SERVICE_UNAVAILABLE","status":503}""")

        assertEquals("ok", api.liveness().status)
        val readiness = api.readiness()
        assertFalse(readiness.isSuccessful)
        assertEquals(503, readiness.code())
        assertEquals(
            "SERVICE_UNAVAILABLE", ProblemDetailsParser().parse(readiness.errorBody())?.code
        )
        assertRequest("/health/live")
        assertRequest("/health/ready")
    }

    private fun enqueue(status: Int, body: String) {
        server.enqueue(
            MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json").setBody(body)
        )
    }

    private fun assertRequest(path: String) {
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals(path, request.path)
        assertEquals(0L, request.bodySize)
        assertNull(request.getHeader("Authorization"))
    }
}
