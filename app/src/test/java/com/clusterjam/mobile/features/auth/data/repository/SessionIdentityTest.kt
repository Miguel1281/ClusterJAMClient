package com.clusterjam.mobile.features.auth.data.repository

import com.clusterjam.mobile.core.network.createRetrofit
import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
import com.clusterjam.mobile.features.auth.domain.model.*
import com.google.gson.Gson
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionIdentityTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: MemoryStorage
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        storage = MemoryStorage()
        repository =
            AuthRepositoryImpl(
                createRetrofit(server.url("/").toString()).create(AuthApiService::class.java),
                storage,
                nowMillis = { NOW },
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loginNeverGrantsAccessWhileIdentityIsPending() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/api/v1/sessions") return json(200, session())
                    started.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    return json(200, identity("cliente"))
                }
            }
        val login = async(Dispatchers.IO) { repository.login(credentials()) }
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS))
            assertEquals(SessionState.Checking, repository.sessionState.value)
            assertNotNull(storage.storedSession)
        } finally {
            release.countDown()
        }
        val current = login.await().getOrThrow()
        assertEquals(VerifiedIdentity(USER_ID, EMAIL, AppRole.CLIENT), current)
        assertEquals(SessionState.Authenticated(current), repository.sessionState.value)
    }

    @Test
    fun administratorLoginClearsTokensAndClosesOnlyItsCurrentAndroidSession() = runBlocking {
        server.enqueue(json(200, session()))
        server.enqueue(json(200, identity("administrador")))
        server.enqueue(MockResponse().setResponseCode(204))

        val failure = repository.login(credentials()).exceptionOrNull() as AuthFailure

        assertEquals("CLIENT_APP_ACCESS_DENIED", failure.code)
        assertNull(failure.status)
        assertNull(storage.storedSession)
        assertTrue(repository.sessionState.value is SessionState.AccessDenied)
        assertRequest("POST", "/api/v1/sessions", null)
        assertRequest("GET", "/api/v1/sessions/current", "Bearer access")
        assertRequest("DELETE", "/api/v1/sessions/current", "Bearer access")
        assertEquals(3, server.requestCount)
    }

    @Test
    fun administratorStaysDeniedWhenRemoteLogoutReturns401WithoutRenewingIt() = runBlocking {
        storage.storedSession = savedSession()
        server.enqueue(json(200, identity("administrador")))
        server.enqueue(json(401, """{"code":"INVALID_ACCESS_TOKEN"}"""))

        val failure = repository.restoreSession().exceptionOrNull() as AuthFailure

        assertEquals("CLIENT_APP_ACCESS_DENIED", failure.code)
        assertNull(storage.storedSession)
        assertRequest("GET", "/api/v1/sessions/current", "Bearer access")
        assertRequest("DELETE", "/api/v1/sessions/current", "Bearer access")
        assertEquals(2, server.requestCount)
        assertTrue(repository.sessionState.value is SessionState.AccessDenied)
    }

    @Test
    fun administratorRemainsDeniedDuringFailedRemoteCleanup() = runBlocking {
        val deleting = CountDownLatch(1)
        val release = CountDownLatch(1)
        storage.storedSession = savedSession()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.method == "GET") return json(200, identity("administrador"))
                    deleting.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    return MockResponse().setResponseCode(503)
                }
            }
        val restoration = async(Dispatchers.IO) { repository.restoreSession() }
        try {
            assertTrue(deleting.await(10, TimeUnit.SECONDS))
            assertNull(storage.storedSession)
            assertTrue(repository.sessionState.value is SessionState.AccessDenied)
        } finally {
            release.countDown()
        }
        assertEquals(
            "CLIENT_APP_ACCESS_DENIED",
            (restoration.await().exceptionOrNull() as AuthFailure).code,
        )
    }

    @Test
    fun restorationVerifiesClientInsteadOfTrustingStoredTokens() = runBlocking {
        storage.storedSession = savedSession()
        server.enqueue(json(200, identity("cliente")))
        val identity = repository.restoreSession().getOrThrow()
        assertEquals(AppRole.CLIENT, identity?.appRole)
        assertEquals(
            SessionState.Authenticated(requireNotNull(identity)),
            repository.sessionState.value,
        )
        assertRequest("GET", "/api/v1/sessions/current", "Bearer access")
    }

    @Test
    fun roleMustBePresentAndMatchAnExactContractValue() = runBlocking {
        for (role in listOf(null, "", "Cliente", "cliente ", "administrator", "future-role")) {
            storage.storedSession = savedSession()
            // Explicit local dismissal makes each case an independent authentication attempt.
            repository.discardSession().getOrThrow()
            storage.storedSession = savedSession()
            server.enqueue(json(200, identity(role)))
            val failure = repository.restoreSession().exceptionOrNull() as AuthFailure
            assertEquals("INVALID_IDENTITY_ROLE", failure.code)
            assertNull(failure.status)
            assertNull(storage.storedSession)
            assertTrue(repository.sessionState.value is SessionState.AccessDenied)
            assertRequest("GET", "/api/v1/sessions/current", "Bearer access")
        }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun serverRoleRejectionKeepsIts403AndDoesNotRenewOrBecomeLocalAdministratorDenial() =
        runBlocking {
            storage.storedSession = savedSession()
            server.enqueue(json(403, """{"code":"INVALID_APP_ROLE","traceId":"role-trace"}"""))
            val failure = repository.restoreSession().exceptionOrNull() as AuthFailure
            assertEquals("INVALID_APP_ROLE", failure.code)
            assertEquals(403, failure.status)
            assertEquals("role-trace", failure.traceId)
            assertNull(storage.storedSession)
            assertTrue(repository.sessionState.value is SessionState.AccessDenied)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun mismatchedUserCannotBecomeAuthenticated() = runBlocking {
        storage.storedSession = savedSession()
        server.enqueue(json(200, identity("cliente", "other-user")))
        val failure = repository.currentIdentity().exceptionOrNull() as AuthFailure
        assertEquals("IDENTITY_MISMATCH", failure.code)
        assertNull(storage.storedSession)
        assertTrue(repository.sessionState.value is SessionState.AccessDenied)
    }

    @Test
    fun temporaryFailureAfterLoginRetainsTokensAndRetriesOnlyIdentity() = runBlocking {
        server.enqueue(json(200, session()))
        server.enqueue(json(503, """{"code":"SERVICE_UNAVAILABLE"}"""))
        assertTrue(repository.login(credentials()).isFailure)
        assertNotNull(storage.storedSession)
        assertTrue(repository.sessionState.value is SessionState.RecoverableError)
        server.enqueue(json(200, identity("cliente")))
        repository.currentIdentity().getOrThrow()
        assertTrue(repository.sessionState.value is SessionState.Authenticated)
        assertRequest("POST", "/api/v1/sessions", null)
        assertRequest("GET", "/api/v1/sessions/current", "Bearer access")
        assertRequest("GET", "/api/v1/sessions/current", "Bearer access")
    }

    @Test
    fun temporaryRestorationFailureKeepsTokensAndAccessBlocked() = runBlocking {
        storage.storedSession = savedSession()
        server.enqueue(json(503, """{"code":"SERVICE_UNAVAILABLE"}"""))
        assertTrue(repository.restoreSession().isFailure)
        assertEquals(savedSession(), storage.storedSession)
        assertTrue(repository.sessionState.value is SessionState.RecoverableError)
    }

    @Test
    fun networkFailureOnRestorationKeepsTokensAndAccessBlocked() = runBlocking {
        storage.storedSession = savedSession()
        server.shutdown()
        val failure = repository.restoreSession().exceptionOrNull() as AuthFailure
        assertEquals("NETWORK_ERROR", failure.code)
        assertEquals(savedSession(), storage.storedSession)
        assertTrue(repository.sessionState.value is SessionState.RecoverableError)
    }

    @Test
    fun foregroundRecheckCanRevokePreviouslyVerifiedCustomerAccess() = runBlocking {
        storage.storedSession = savedSession()
        server.enqueue(json(200, identity("cliente")))
        repository.restoreSession().getOrThrow()
        repository.invalidateIdentity()
        assertEquals(SessionState.Checking, repository.sessionState.value)
        server.enqueue(json(200, identity("administrador")))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.currentIdentity().isFailure)
        assertTrue(repository.sessionState.value is SessionState.AccessDenied)
        assertNull(storage.storedSession)
    }

    @Test
    fun failedLocalErasureStillBlocksAdministratorAndDoesNotRestoreRejectedTokens() = runBlocking {
        storage.storedSession = savedSession()
        storage.failClear = true
        server.enqueue(json(200, identity("administrador")))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.restoreSession().isFailure)
        val state = repository.sessionState.value as SessionState.AccessDenied
        assertEquals("CLIENT_APP_ACCESS_DENIED", state.failure.code)
        assertEquals("STORAGE_ERROR", state.cleanupFailure?.code)
        assertTrue(repository.restoreSession().isFailure)
        assertTrue(repository.renewSession().isFailure)
        assertEquals(2, server.requestCount)
        storage.failClear = false
        repository.discardSession().getOrThrow()
        assertNull(storage.storedSession)
        assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
    }

    @Test
    fun administratorAfterTokenRenewalIsClosedWithTheRenewedAndroidToken() = runBlocking {
        storage.storedSession = savedSession().copy(expiresAtMillis = NOW - 1)
        server.enqueue(json(200, session("rotated-access", "rotated-refresh")))
        server.enqueue(json(200, identity("administrador")))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.restoreSession().isFailure)
        assertRequest("POST", "/api/v1/session-renewals", null)
        assertRequest("GET", "/api/v1/sessions/current", "Bearer rotated-access")
        assertRequest("DELETE", "/api/v1/sessions/current", "Bearer rotated-access")
        assertNull(storage.storedSession)
    }

    private fun credentials() = AuthCredentials(EMAIL, "Password123!")

    private fun savedSession() =
        AuthSession(AuthIdentity(USER_ID, EMAIL), "access", "refresh", NOW + 3_600_000L)

    private fun identity(role: String?, userId: String = USER_ID) =
        Gson()
            .toJson(
                mapOf("userId" to userId, "email" to EMAIL, "appRole" to role),
            )

    private fun session(access: String = "access", refresh: String = "refresh") =
        Gson()
            .toJson(
                mapOf(
                    "userId" to USER_ID,
                    "email" to EMAIL,
                    "accessToken" to access,
                    "refreshToken" to refresh,
                    "expiresIn" to 3600,
                    "tokenType" to "bearer",
                ),
            )

    private fun json(status: Int, body: String) =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun assertRequest(method: String, path: String, authorization: String?) {
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals(method, request.method)
        assertEquals(path, request.path)
        assertEquals(authorization, request.getHeader("Authorization"))
    }

    private class MemoryStorage : SecureStorage {
        @Volatile var storedSession: AuthSession? = null
        var failClear = false

        override fun getSession() = storedSession

        override fun saveSession(session: AuthSession) {
            this.storedSession = session
        }

        override fun clear() {
            if (failClear) throw AuthFailure("STORAGE_ERROR", storageAction = StorageAction.CLEAR)
            storedSession = null
        }
    }

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
        private const val EMAIL = "client@example.com"
        private const val NOW = 1_700_000_000_000L
    }
}
