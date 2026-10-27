package com.clusterjam.mobile.features.auth.data.repository

import com.clusterjam.mobile.core.network.createRetrofit
import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
import com.clusterjam.mobile.features.auth.domain.model.AppRole
import com.clusterjam.mobile.features.auth.domain.model.AuthCredentials
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.AuthIdentity
import com.clusterjam.mobile.features.auth.domain.model.AuthSession
import com.clusterjam.mobile.features.auth.domain.model.RegistrationStatus
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.model.VerifiedIdentity
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: InMemorySecureStorage
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        storage = InMemorySecureStorage()
        val api = createRetrofit(server.url("/").toString()).create(AuthApiService::class.java)
        repository = AuthRepositoryImpl(api, storage, nowMillis = { NOW_MILLIS })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loginStoresTokensAndConvertsLifetimeFromSeconds() = runBlocking {
        enqueueJson(200, sessionJson())
        enqueueJson(200, """{"userId":"$USER_ID","email":"$EMAIL","appRole":"cliente"}""")

        val identity =
            repository
                .login(
                    AuthCredentials(" $EMAIL ", PASSWORD),
                )
                .getOrThrow()

        assertEquals(VerifiedIdentity(USER_ID, EMAIL, AppRole.CLIENT), identity)
        val session = requireNotNull(storage.storedSession)
        assertEquals(USER_ID, session.identity.userId)
        assertEquals(EMAIL, session.identity.email)
        assertEquals("access-token", session.accessToken)
        assertEquals("refresh-token", session.refreshToken)
        assertEquals(NOW_MILLIS + 3_600_000L, session.expiresAtMillis)
        assertRequest(
            "POST",
            "/api/v1/sessions",
            mapOf("email" to EMAIL, "password" to PASSWORD),
        )
        assertRequest("GET", "/api/v1/sessions/current", authorization = "Bearer access-token")
    }

    @Test
    fun registrationAcceptsBothStatusesWithoutCreatingSession() = runBlocking {
        for (status in RegistrationStatus.values()) {
            enqueueJson(
                201,
                """{"userId":"$USER_ID","email":"$EMAIL","status":"${status.name}"}""",
            )

            val registration =
                repository
                    .register(
                        AuthCredentials(EMAIL, PASSWORD),
                    )
                    .getOrThrow()

            assertEquals(AuthIdentity(USER_ID, EMAIL), registration.identity)
            assertEquals(status, registration.status)
            assertNull(storage.storedSession)
            assertRequest(
                "POST",
                "/api/v1/registrations",
                mapOf("email" to EMAIL, "password" to PASSWORD),
            )
        }
    }

    @Test
    fun renewalSendsRefreshTokenAndReplacesBothStoredTokens() = runBlocking {
        storage.storedSession = existingSession()
        enqueueJson(200, sessionJson("new-access-token", "new-refresh-token"))

        val session = repository.renewSession().getOrThrow()

        assertEquals("new-access-token", session.accessToken)
        assertEquals("new-refresh-token", session.refreshToken)
        assertEquals(session, storage.storedSession)
        assertRequest(
            "POST",
            "/api/v1/session-renewals",
            mapOf("refreshToken" to "refresh-token"),
        )
    }

    @Test
    fun currentIdentityUsesBearerAndVerifiesClientRole() = runBlocking {
        storage.storedSession = existingSession()
        enqueueJson(
            200,
            """{"userId":"$USER_ID","email":"$EMAIL","appRole":"cliente"}""",
        )

        assertEquals(
            VerifiedIdentity(USER_ID, EMAIL, AppRole.CLIENT),
            repository.currentIdentity().getOrThrow(),
        )

        assertRequest("GET", "/api/v1/sessions/current", authorization = "Bearer access-token")
    }

    @Test
    fun logoutAccepts204WithoutBodyAndClearsLocalSession() = runBlocking {
        storage.storedSession = existingSession()
        server.enqueue(MockResponse().setResponseCode(204))

        repository.logout().getOrThrow()

        assertNull(storage.storedSession)
        assertRequest("DELETE", "/api/v1/sessions/current", authorization = "Bearer access-token")
    }

    @Test
    fun recoveryRequestAcceptsNeutralResponseWithoutAuthorization() = runBlocking {
        enqueueJson(
            200,
            """
            {"message":"If the account can receive recovery emails, you will receive instructions."}
            """
                .trimIndent(),
        )

        repository.requestPasswordRecovery(" $EMAIL ").getOrThrow()

        assertNull(storage.storedSession)
        assertRequest(
            "POST",
            "/api/v1/password-recovery-requests",
            mapOf("email" to EMAIL),
        )
    }

    @Test
    fun passwordRecoveryPreservesCodeLeadingZeroAndAccepts204() = runBlocking {
        storage.storedSession = existingSession()
        server.enqueue(MockResponse().setResponseCode(204))

        repository.recoverPassword(EMAIL, "012345", "NewStrongPassword123!").getOrThrow()

        assertNull(storage.storedSession)
        assertRequest(
            "POST",
            "/api/v1/password-recoveries",
            mapOf(
                "email" to EMAIL,
                "code" to "012345",
                "newPassword" to "NewStrongPassword123!",
            ),
        )
    }

    @Test
    fun validationProblemPreservesErrorsAndTraceId() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(
                    """
                    {
                        "type":"about:blank",
                        "title":"Unprocessable Entity",
                        "status":422,
                        "detail":"Request validation failed.",
                        "instance":"/api/v1/sessions",
                        "code":"VALIDATION_ERROR",
                        "errors":["email must be an email"],
                        "traceId":"4bf92f3577b34da6a3ce929d0e0e4736"
                    }
                    """
                        .trimIndent(),
                ),
        )

        val failure =
            repository.login(AuthCredentials(EMAIL, PASSWORD)).exceptionOrNull() as AuthFailure

        assertEquals("VALIDATION_ERROR", failure.code)
        assertEquals(422, failure.status)
        assertEquals(listOf("email must be an email"), failure.validationErrors)
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", failure.traceId)
        assertNull(storage.storedSession)
    }

    @Test
    fun sessionResponseWithoutTokensIsRejectedWithoutPersistingIt() = runBlocking {
        enqueueJson(200, """{"userId":"$USER_ID","email":"$EMAIL"}""")

        val failure =
            repository.login(AuthCredentials(EMAIL, PASSWORD)).exceptionOrNull() as AuthFailure

        assertEquals("INVALID_RESPONSE", failure.code)
        assertNull(storage.storedSession)
    }

    @Test
    fun sessionWithInvalidLifetimeIsRejectedWithoutPersistingIt() = runBlocking {
        enqueueJson(200, sessionJson(expiresIn = 0))

        val failure =
            repository.login(AuthCredentials(EMAIL, PASSWORD)).exceptionOrNull() as AuthFailure

        assertEquals("INVALID_RESPONSE", failure.code)
        assertNull(storage.storedSession)
    }

    @Test
    fun confirmationPreservesLeadingZeroAndOpensOnlyAVerifiedCustomerSession() = runBlocking {
        enqueueJson(200, sessionJson())
        enqueueJson(200, """{"userId":"$USER_ID","email":"$EMAIL","appRole":"cliente"}""")

        repository.confirmEmail(" $EMAIL ", "012345").getOrThrow()

        assertTrue(repository.sessionState.value is SessionState.Authenticated)
        assertEquals("access-token", storage.storedSession?.accessToken)
        assertRequest(
            "POST",
            "/api/v1/registrations/email-confirmations",
            mapOf("email" to EMAIL, "code" to "012345"),
        )
        assertRequest("GET", "/api/v1/sessions/current", authorization = "Bearer access-token")
    }

    @Test
    fun identityRetryAfterConfirmationNeverSubmitsTheConsumedCodeAgain() = runBlocking {
        enqueueJson(200, sessionJson())
        enqueueJson(503, """{"code":"SERVICE_UNAVAILABLE"}""")

        // Confirmation completed even though access is still waiting for identity verification.
        repository.confirmEmail(EMAIL, "012345").getOrThrow()
        assertTrue(repository.sessionState.value is SessionState.RecoverableError)
        assertEquals("refresh-token", storage.storedSession?.refreshToken)
        enqueueJson(200, """{"userId":"$USER_ID","email":"$EMAIL","appRole":"cliente"}""")
        repository.currentIdentity().getOrThrow()

        assertTrue(repository.sessionState.value is SessionState.Authenticated)
        assertRequest(
            "POST",
            "/api/v1/registrations/email-confirmations",
            mapOf("email" to EMAIL, "code" to "012345"),
        )
        repeat(2) {
            assertRequest("GET", "/api/v1/sessions/current", authorization = "Bearer access-token")
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun confirmationErrorsNeverReplayAndResendUses202WithRetryAfter() = runBlocking {
        repository.restoreSession().getOrThrow()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("Retry-After", "5")
                .setBody("""{"code":"INVALID_EMAIL_CONFIRMATION_CODE"}"""),
        )
        val failure = repository.confirmEmail(EMAIL, "012345").exceptionOrNull() as AuthFailure
        assertEquals("INVALID_EMAIL_CONFIRMATION_CODE", failure.code)
        assertEquals(401, failure.status)
        assertEquals(5000L, failure.retryAfterMillis)
        assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
        assertNull(storage.storedSession)
        // NOW_MILLIS is 2023-11-14 22:13:20 UTC: exercise HTTP-date as well as seconds.
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setHeader("Retry-After", "Tue, 14 Nov 2023 22:13:23 GMT")
                .setBody("""{"message":"Neutral confirmation request."}"""),
        )
        assertEquals(
            3000L,
            repository.requestEmailConfirmation(" $EMAIL ").getOrThrow().retryAfterMillis,
        )
        assertRequest(
            "POST",
            "/api/v1/registrations/email-confirmations",
            mapOf("email" to EMAIL, "code" to "012345"),
        )
        assertRequest(
            "POST",
            "/api/v1/registrations/email-confirmation-requests",
            mapOf("email" to EMAIL),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("Retry-After", "0")
                .setBody("""{"code":"SERVICE_UNAVAILABLE"}"""),
        )
        val unavailable = repository.confirmEmail(EMAIL, "012345").exceptionOrNull() as AuthFailure
        assertEquals(503, unavailable.status)
        assertRequest(
            "POST",
            "/api/v1/registrations/email-confirmations",
            mapOf("email" to EMAIL, "code" to "012345"),
        )
        assertEquals(3, server.requestCount)
    }

    private fun enqueueJson(status: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun assertRequest(
        method: String,
        path: String,
        body: Map<String, String>? = null,
        authorization: String? = null,
    ) {
        val request: RecordedRequest = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals(method, request.method)
        assertEquals(path, request.path)
        assertEquals(authorization, request.getHeader("Authorization"))
        if (body == null) {
            assertEquals(0L, request.bodySize)
        } else {
            assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
            assertEquals(
                Gson().toJsonTree(body),
                Gson().fromJson(request.body.readUtf8(), JsonObject::class.java),
            )
        }
    }

    private fun existingSession() =
        AuthSession(
            AuthIdentity(USER_ID, EMAIL),
            "access-token",
            "refresh-token",
            NOW_MILLIS + 3_600_000L,
        )

    private fun sessionJson(
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        expiresIn: Int = 3600,
    ): String =
        Gson()
            .toJson(
                mapOf(
                    "userId" to USER_ID,
                    "email" to EMAIL,
                    "accessToken" to accessToken,
                    "refreshToken" to refreshToken,
                    "expiresIn" to expiresIn,
                    "tokenType" to "bearer",
                ),
            )

    private class InMemorySecureStorage : SecureStorage {
        var storedSession: AuthSession? = null

        override fun saveSession(session: AuthSession) {
            storedSession = session
        }

        override fun getSession(): AuthSession? = storedSession

        override fun clear() {
            storedSession = null
        }
    }

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
        private const val EMAIL = "user@clusterjam.com"
        private const val PASSWORD = "StrongPassword123!"
        private const val NOW_MILLIS = 1_700_000_000_000L
    }
}
