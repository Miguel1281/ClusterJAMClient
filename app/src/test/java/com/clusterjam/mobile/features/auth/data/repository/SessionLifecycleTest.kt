package com.clusterjam.mobile.features.auth.data.repository

import com.clusterjam.mobile.core.network.createRetrofit
import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
import com.clusterjam.mobile.features.auth.domain.model.*
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import okhttp3.mockwebserver.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.http.*

class SessionLifecycleTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: MemoryStorage
    private lateinit var repository: AuthRepositoryImpl
    private lateinit var probe: ProbeApi
    private var now = NOW
    private val path
        get() = server.url("/api/v1/probe").toString()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        storage = MemoryStorage()
        val retrofit = createRetrofit(server.url("/").toString())
        probe = retrofit.create(ProbeApi::class.java)
        repository =
            AuthRepositoryImpl(
                retrofit.create(AuthApiService::class.java),
                storage,
                nowMillis = { now },
                apiBaseUrl = server.url("/").toString(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun authenticate() {
        storage.storedSession = saved()
        server.enqueue(json(200, IDENTITY))
        repository.restoreSession().getOrThrow()
    }

    @Test
    fun expiredRequestsShareOneRenewalAndOrdinaryCallsOverlap() = runBlocking {
        authenticate()
        now += 3_600_000
        val renewing = CountDownLatch(1)
        val releaseRenewal = CountDownLatch(1)
        val reading = CountDownLatch(2)
        val releaseReads = CountDownLatch(1)
        val renewals = AtomicInteger()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/api/v1/session-renewals") {
                        renewals.incrementAndGet()
                        renewing.countDown()
                        check(releaseRenewal.await(10, TimeUnit.SECONDS))
                        return json(200, tokens("rotated"))
                    }
                    assertEquals("Bearer rotated", request.getHeader("Authorization"))
                    reading.countDown()
                    check(releaseReads.await(10, TimeUnit.SECONDS))
                    return json(200, "{}")
                }
            }
        val calls =
            List(2) { async(Dispatchers.IO) { repository.execute { probe.read(path, it) } } }
        try {
            assertTrue(renewing.await(10, TimeUnit.SECONDS))
            releaseRenewal.countDown()
            assertTrue(
                "Ordinary protected requests must run concurrently",
                reading.await(10, TimeUnit.SECONDS),
            )
        } finally {
            releaseRenewal.countDown()
            releaseReads.countDown()
        }
        calls.forEach { it.await().getOrThrow().body()?.close() }
        assertEquals(1, renewals.get())
        assertEquals("rotated-refresh", storage.storedSession?.refreshToken)
    }

    @Test
    fun concurrent401sReuseRenewalAndRepeatedRejectionEndsSession() = runBlocking {
        authenticate()
        val firstReads = CountDownLatch(2)
        val renewals = AtomicInteger()
        val rejectedReads = AtomicInteger()
        var rejectAll = false
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/api/v1/session-renewals") {
                        val number = renewals.incrementAndGet()
                        return json(200, tokens("rotated-$number"))
                    }
                    if (rejectAll) {
                        rejectedReads.incrementAndGet()
                        return json(401, """{"code":"INVALID_ACCESS_TOKEN"}""")
                    }
                    if (request.getHeader("Authorization") == "Bearer access") {
                        firstReads.countDown()
                        check(firstReads.await(10, TimeUnit.SECONDS))
                        return json(401, "{}")
                    }
                    assertEquals("Bearer rotated-1", request.getHeader("Authorization"))
                    return json(200, "{}")
                }
            }
        List(2) { async(Dispatchers.IO) { repository.execute { probe.read(path, it) } } }
            .forEach { it.await().getOrThrow().body()?.close() }
        assertEquals(1, renewals.get())
        rejectAll = true
        assertEquals(
            "SESSION_ENDED",
            (repository.execute { probe.read(path, it) }.exceptionOrNull() as AuthFailure).code,
        )
        assertEquals(2, renewals.get())
        assertEquals(2, rejectedReads.get())
        assertNull(storage.storedSession)
        assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
    }

    @Test
    fun transientRefreshFailuresKeepTokensAndRespectRetryAfterBut401EndsSession() = runBlocking {
        storage.storedSession = saved().copy(expiresAtMillis = NOW - 1)
        for ((index, status) in listOf(409, 429, 503).withIndex()) {
            server.enqueue(
                json(status, """{"code":"TEMPORARY_REFRESH_FAILURE"}""")
                    .setHeader("Retry-After", "2"),
            )
            val failure = repository.restoreSession().exceptionOrNull() as AuthFailure
            assertEquals(status, failure.status)
            assertEquals("TEMPORARY_REFRESH_FAILURE", failure.code)
            assertEquals(2000L, failure.retryAfterMillis)
            assertEquals("refresh", storage.storedSession?.refreshToken)
            assertTrue(repository.sessionState.value is SessionState.RecoverableError)
            assertTrue(repository.restoreSession().isFailure)
            assertEquals(index + 1, server.requestCount)
            now += 2000
        }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(
            "NETWORK_ERROR",
            (repository.restoreSession().exceptionOrNull() as AuthFailure).code,
        )
        assertNotNull(storage.storedSession)
        server.enqueue(json(401, """{"code":"INVALID_REFRESH_TOKEN"}"""))
        assertNull(repository.restoreSession().getOrThrow())
        assertNull(storage.storedSession)
        assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
        assertEquals(5, server.requestCount)
    }

    @Test
    fun discardInvalidatesPendingLoginConfirmationAndRenewal() = runBlocking {
        for (operation in listOf("login", "confirmation", "renewal")) {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        started.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        return json(200, tokens("late"))
                    }
                }
            if (operation == "renewal") storage.storedSession = saved()
            val pending =
                async(Dispatchers.IO) {
                    when (operation) {
                        "login" -> repository.login(AuthCredentials(EMAIL, "Password123!"))
                        "confirmation" -> repository.confirmEmail(EMAIL, "012345")
                        else -> repository.renewSession()
                    }
                }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            val discard = async(start = CoroutineStart.UNDISPATCHED) { repository.discardSession() }
            try {
                assertEquals(SessionState.Checking, repository.sessionState.value)
            } finally {
                release.countDown()
            }
            val result = pending.await()
            if (operation == "confirmation") assertTrue(result.isSuccess) // OTP already consumed.
            else assertEquals("SESSION_CHANGED", (result.exceptionOrNull() as AuthFailure).code)
            discard.await().getOrThrow()
            assertNull(storage.storedSession)
            assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun storageFaultsBlockAccessUntilCleanupAndRemoteLogoutFailureStillClearsLocally() =
        runBlocking {
            storage.failRead = true
            assertEquals(
                StorageAction.READ,
                (repository.restoreSession().exceptionOrNull() as AuthFailure).storageAction,
            )
            storage.failRead = false
            authenticate()
            storage.failSave = true
            server.enqueue(json(200, tokens("rotated")))
            assertEquals(
                StorageAction.SAVE,
                (repository.renewSession().exceptionOrNull() as AuthFailure).storageAction,
            )
            assertTrue(repository.sessionState.value is SessionState.AccessDenied)
            assertTrue(repository.restoreSession().isFailure)
            storage.failClear = true
            assertEquals(
                StorageAction.CLEAR,
                (repository.discardSession().exceptionOrNull() as AuthFailure).storageAction,
            )
            assertTrue(repository.sessionState.value is SessionState.AccessDenied)
            assertTrue(repository.renewSession().isFailure)
            storage.failClear = false
            storage.failSave = false
            repository.discardSession().getOrThrow()
            authenticate()
            server.enqueue(json(503, "{}"))
            assertTrue(repository.logout().isFailure)
            assertNull(storage.storedSession)
            assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
        }

    @Test
    fun lateProtectedResponseCannotRestoreSessionAfterFailedRemoteLogout() = runBlocking {
        authenticate()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.method == "DELETE") return json(503, "{}")
                    started.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    return json(200, "{}")
                }
            }
        val pending = async(Dispatchers.IO) { repository.execute { probe.read(path, it) } }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        try {
            assertTrue(repository.logout().isFailure)
            assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
            assertNull(storage.storedSession)
        } finally {
            release.countDown()
        }
        assertEquals("SESSION_CHANGED", (pending.await().exceptionOrNull() as AuthFailure).code)
        assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
    }

    @Test
    fun sessionAccessRestrictsTargetsAndNeverReplaysPostOrEndsSessionForPermission403() =
        runBlocking {
            authenticate()
            MockWebServer().use { external ->
                external.start()
                assertTrue(
                    repository
                        .execute { probe.read(external.url("/api/v1/probe").toString(), it) }
                        .isFailure,
                )
                assertEquals(0, external.requestCount)
            }
            assertTrue(
                repository
                    .execute { probe.read(server.url("/api/v1/sessions").toString(), it) }
                    .isFailure,
            )
            assertEquals(1, server.requestCount) // Only initial identity verification.
            server.enqueue(json(403, """{"code":"PERMISSION_DENIED"}"""))
            assertEquals(
                403,
                (repository.execute { probe.read(path, it) }.exceptionOrNull() as AuthFailure)
                    .status,
            )
            assertTrue(repository.sessionState.value is SessionState.Authenticated)
            server.enqueue(json(401, "{}"))
            assertEquals(
                401,
                (repository.execute { probe.post(path, it) }.exceptionOrNull() as AuthFailure)
                    .status,
            )
            assertEquals(3, server.requestCount)
            server.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", "/api/v1/sessions"),
            )
            assertTrue(repository.execute { probe.read(path, it) }.isFailure)
            assertEquals(
                4,
                server.requestCount,
            ) // The public redirect never receives Authorization.
            assertNotNull(storage.storedSession)
        }

    interface ProbeApi {
        @GET
        fun read(
            @Url url: String,
            @Header("Authorization") authorization: String,
        ): Call<ResponseBody>

        @POST
        fun post(
            @Url url: String,
            @Header("Authorization") authorization: String,
        ): Call<ResponseBody>
    }

    private class MemoryStorage : SecureStorage {
        @Volatile var storedSession: AuthSession? = null
        var failRead = false
        var failSave = false
        var failClear = false

        override fun getSession(): AuthSession? {
            if (failRead) throw IOException("Read failed")
            return storedSession
        }

        override fun saveSession(session: AuthSession) {
            if (failSave) throw IOException("Write failed")
            this.storedSession = session
        }

        override fun clear() {
            if (failClear) throw IOException("Clear failed")
            storedSession = null
        }
    }

    private fun saved() =
        AuthSession(AuthIdentity("user", EMAIL), "access", "refresh", NOW + 3_600_000)

    private fun tokens(access: String) =
        """{"userId":"user","email":"$EMAIL","accessToken":"$access","refreshToken":"$access-refresh","expiresIn":3600,"tokenType":"bearer"}"""

    private fun json(status: Int, body: String) =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    companion object {
        private const val NOW = 1_700_000_000_000L
        private const val EMAIL = "client@example.com"
        private const val IDENTITY =
            """{"userId":"user","email":"client@example.com","appRole":"cliente"}"""
    }
}
