package com.clusterjam.mobile.features.auth.data.repository

import com.clusterjam.mobile.BuildConfig
import com.clusterjam.mobile.core.network.SessionAccess
import com.clusterjam.mobile.core.network.isProtectedApiRequest
import com.clusterjam.mobile.core.network.isProtectedProfileRequest
import com.clusterjam.mobile.core.network.parseRetryAfterMillis
import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.mapper.mapAuthError
import com.clusterjam.mobile.features.auth.data.mapper.toIdentity
import com.clusterjam.mobile.features.auth.data.mapper.toRegistration
import com.clusterjam.mobile.features.auth.data.mapper.toSession
import com.clusterjam.mobile.features.auth.data.mapper.toVerifiedIdentity
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
import com.clusterjam.mobile.features.auth.data.remote.dto.ConfirmEmailRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.EmailConfirmationRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.LoginRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.PasswordRecoveryRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RecoverPasswordRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RefreshSessionRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RegistrationRequestDto
import com.clusterjam.mobile.features.auth.domain.model.AppRole
import com.clusterjam.mobile.features.auth.domain.model.AuthCredentials
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.AuthIdentity
import com.clusterjam.mobile.features.auth.domain.model.AuthSession
import com.clusterjam.mobile.features.auth.domain.model.EmailConfirmationRequestResult
import com.clusterjam.mobile.features.auth.domain.model.PasswordRecoveryResult
import com.clusterjam.mobile.features.auth.domain.model.RegistrationResult
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.model.StorageAction
import com.clusterjam.mobile.features.auth.domain.model.VerifiedIdentity
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidationError
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response

class AuthRepositoryImpl(
    private val apiService: AuthApiService,
    private val secureStorage: SecureStorage,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val apiBaseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val profileBaseUrl: String? = null,
) : AuthRepository, SessionAccess {
    private val sessionMutex = Mutex()
    private val sessionFence = Any()
    private val generation = AtomicLong()
    private val revision = AtomicLong()
    private var refreshRetryAt = 0L
    private var refreshFailure: AuthFailure? = null
    private var identityRetryAt = 0L
    private var identityFailure: AuthFailure? = null
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Checking)
    override val sessionState = _sessionState.asStateFlow()
    private var rejectedSession: AuthFailure? = null

    override fun invalidateIdentity() {
        _sessionState.update { if (it is SessionState.Authenticated) SessionState.Checking else it }
    }

    override suspend fun login(credentials: AuthCredentials): Result<VerifiedIdentity> {
        val expected = beginSessionChange()
        return capture {
            val request = credentials.copy(email = credentials.email.trim())
            validateEmail(request.email)
            if (request.password.isEmpty()) invalid(AuthValidationError.PASSWORD_REQUIRED)
            sessionMutex.withLock {
                ensureCurrent(expected)
                if (rejectedSession != null) clearSessionLocked(expected)
                val session =
                    apiService
                        .login(LoginRequestDto(request.email, request.password))
                        .toSession(nowMillis())
                establishSessionLocked(session, expected)
            }
        }
    }

    override suspend fun register(
        credentials: AuthCredentials,
    ): Result<RegistrationResult> = capture {
        val request = credentials.copy(email = credentials.email.trim())
        validateEmail(request.email)
        AuthValidator.passwordError(request.password)?.let { invalid(it) }
        apiService
            .register(RegistrationRequestDto(request.email, request.password))
            .toRegistration()
    }

    override suspend fun confirmEmail(email: String, code: String): Result<Unit> {
        val expected = beginSessionChange()
        return capture {
            val normalized = email.trim()
            validateEmail(normalized)
            AuthValidator.codeError(code)?.let { invalid(it) }
            sessionMutex.withLock {
                ensureCurrent(expected)
                if (rejectedSession != null) clearSessionLocked(expected)
                val response = apiService.confirmEmail(ConfirmEmailRequestDto(normalized, code))
                if (!response.isSuccessful) throw HttpException(response)
                try {
                    publish(expected, SessionState.Checking)
                    if (response.code() != 200) throw AuthFailure("INVALID_RESPONSE")
                    val session =
                        response.body()?.toSession(nowMillis())
                            ?: throw AuthFailure("INVALID_RESPONSE")
                    establishSessionLocked(session, expected)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val failure = failure(error)
                    if (
                        generation.get() == expected &&
                            (_sessionState.value == SessionState.Checking ||
                                _sessionState.value == SessionState.Unauthenticated)
                    ) {
                        publish(expected, SessionState.RecoverableError(failure))
                    }
                    // The OTP was already consumed. Recovery continues through the global session
                    // state.
                }
                Unit
            }
        }
    }

    override suspend fun requestEmailConfirmation(
        email: String,
    ): Result<EmailConfirmationRequestResult> = capture {
        val normalized = email.trim()
        validateEmail(normalized)
        val response = apiService.requestEmailConfirmation(EmailConfirmationRequestDto(normalized))
        if (!response.isSuccessful) throw HttpException(response)
        if (response.code() != 202) throw AuthFailure("INVALID_RESPONSE")
        EmailConfirmationRequestResult(
            parseRetryAfterMillis(response.headers()["Retry-After"], nowMillis()),
        )
    }

    override suspend fun restoreSession(): Result<VerifiedIdentity?> {
        val expected = generation.get()
        return capture {
            sessionMutex.withLock {
                ensureCurrent(expected)
                rejectedSession?.let { throw it }
                publish(expected, SessionState.Checking)
                try {
                    if (readSessionLocked(expected) == null) {
                        publish(expected, SessionState.Unauthenticated)
                        return@withLock null
                    }
                    readIdentityLocked(expected)
                } catch (_: SessionEnded) {
                    null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val failure = failure(error)
                    if (
                        generation.get() == expected &&
                            _sessionState.value !is SessionState.AccessDenied
                    ) {
                        publish(expected, SessionState.RecoverableError(failure))
                    }
                    throw failure
                }
            }
        }
    }

    override suspend fun currentIdentity(): Result<VerifiedIdentity> {
        val expected = generation.get()
        return capture { sessionMutex.withLock { readIdentityLocked(expected) } }
    }

    override suspend fun renewSession(): Result<AuthSession> {
        val expected = generation.get()
        val requestedRevision = revision.get()
        return capture {
            sessionMutex.withLock {
                val current = requireSession(expected)
                if (revision.get() != requestedRevision) current else renewLocked(current, expected)
            }
        }
    }

    override suspend fun logout(): Result<Unit> {
        val expected = beginSessionChange(blockAccess = true)
        return capture {
            sessionMutex.withLock {
                ensureCurrent(expected)
                val previouslyRejected = rejectedSession != null
                try {
                    if (!previouslyRejected && readSessionLocked(expected) != null) {
                        try {
                            authorizedLocked(expected) { token ->
                                apiService.logout(token).requireSuccess()
                            }
                        } catch (_: SessionEnded) {
                            // Local cleanup still runs for an expired or rejected session.
                        }
                    }
                } finally {
                    if (generation.get() == expected) clearSessionLocked(expected)
                }
            }
        }
    }

    override suspend fun discardSession(): Result<Unit> {
        val expected = beginSessionChange(blockAccess = true)
        return capture { sessionMutex.withLock { clearSessionLocked(expected) } }
    }

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> = capture {
        val normalized = email.trim()
        validateEmail(normalized)
        val response = apiService.requestPasswordRecovery(PasswordRecoveryRequestDto(normalized))
        if (!response.isSuccessful) throw HttpException(response)
        if (response.code() != 200) throw AuthFailure("INVALID_RESPONSE")
        Unit
    }

    override suspend fun recoverPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<PasswordRecoveryResult> {
        val expected = generation.get()
        return capture {
            sessionMutex.withLock {
                ensureCurrent(expected)
                val normalized = email.trim()
                validateEmail(normalized)
                AuthValidator.codeError(code)?.let { invalid(it) }
                AuthValidator.passwordError(newPassword)?.let { invalid(it) }
                val response =
                    apiService.recoverPassword(
                        RecoverPasswordRequestDto(normalized, code, newPassword),
                    )
                if (!response.isSuccessful) throw HttpException(response)
                if (response.code() != 204) throw AuthFailure("INVALID_RESPONSE")
                try {
                    endSessionLocked(expected)
                    PasswordRecoveryResult()
                } catch (error: Exception) {
                    // 204 is final, even if local cleanup fails or a newer session superseded it.
                    PasswordRecoveryResult(cleanupFailure = failure(error))
                }
            }
        }
    }

    // Login and email confirmation both establish tokens, then use this verification.
    private suspend fun establishSessionLocked(
        session: AuthSession,
        expected: Long,
    ): VerifiedIdentity {
        saveSessionLocked(session, expected)
        return readIdentityLocked(expected)
    }

    private suspend fun readIdentityLocked(expected: Long): VerifiedIdentity {
        ensureCurrent(expected)
        rejectedSession?.let { throw it }
        publish(expected, SessionState.Checking)
        try {
            cooldownFailure(identityRetryAt, identityFailure)?.let { throw it }
            val response = authorizedLocked(expected) { apiService.currentIdentity(it) }
            val session = requireSession(expected)
            if (response.toIdentity().userId != session.identity.userId) {
                throw AuthFailure("IDENTITY_MISMATCH")
            }
            val current = response.toVerifiedIdentity()
            if (current.appRole == AppRole.ADMINISTRATOR) {
                val failure = AuthFailure("CLIENT_APP_ACCESS_DENIED")
                denyAccessLocked(failure, expected)
                try {
                    // Use only the exact Android session, with no renewal or global logout.
                    apiService.logout("Bearer ${session.accessToken}").requireSuccess()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Denial and local token removal remain final if remote logout fails.
                }
                throw failure
            }
            saveSessionLocked(
                session.copy(identity = AuthIdentity(current.userId, current.email)),
                expected,
            )
            identityFailure = null
            identityRetryAt = 0
            publish(expected, SessionState.Authenticated(current))
            return current
        } catch (error: CancellationException) {
            throw error
        } catch (_: SessionEnded) {
            throw SessionEnded()
        } catch (error: Exception) {
            val failure = failure(error)
            if (generation.get() == expected && _sessionState.value !is SessionState.AccessDenied) {
                if (
                    failure.code in
                        setOf("INVALID_IDENTITY_ROLE", "INVALID_APP_ROLE", "IDENTITY_MISMATCH") ||
                        failure.status == 403
                ) {
                    denyAccessLocked(failure, expected)
                } else {
                    synchronized(sessionFence) {
                        ensureCurrent(expected)
                        identityFailure = failure
                        identityRetryAt = retryDeadline(failure)
                        publish(expected, SessionState.RecoverableError(failure))
                    }
                }
            }
            throw failure
        }
    }

    private fun denyAccessLocked(failure: AuthFailure, expected: Long) =
        synchronized(sessionFence) {
            ensureCurrent(expected)
            generation.incrementAndGet()
            rejectedSession = failure
            _sessionState.value = SessionState.AccessDenied(failure)
            try {
                secureStorage.clear()
            } catch (error: Exception) {
                _sessionState.value =
                    SessionState.AccessDenied(failure, storageFailure(error, StorageAction.CLEAR))
            }
        }

    private fun clearSessionLocked(expected: Long) =
        synchronized(sessionFence) {
            ensureCurrent(expected)
            try {
                secureStorage.clear()
                rejectedSession = null
                _sessionState.value = SessionState.Unauthenticated
            } catch (error: Exception) {
                val failure = storageFailure(error, StorageAction.CLEAR)
                rejectedSession = rejectedSession ?: failure
                _sessionState.value =
                    SessionState.AccessDenied(requireNotNull(rejectedSession), failure)
                throw failure
            }
        }

    private fun storageFailure(error: Exception, action: StorageAction): AuthFailure =
        (error as? AuthFailure)?.takeIf { it.code == "STORAGE_ERROR" }
            ?: AuthFailure("STORAGE_ERROR", storageAction = action)

    private suspend fun <T> authorizedLocked(expected: Long, request: suspend (String) -> T): T {
        var session = requireSession(expected)
        val renewed = session.needsRenewal(nowMillis())
        if (renewed) session = renewLocked(session, expected)
        return try {
            request("Bearer ${session.accessToken}").also { ensureCurrent(expected) }
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            ensureCurrent(expected)
            if (renewed) {
                endSessionLocked(expected)
                throw SessionEnded()
            }
            session = renewLocked(session, expected)
            try {
                request("Bearer ${session.accessToken}").also { ensureCurrent(expected) }
            } catch (retry: HttpException) {
                if (retry.code() == 401) {
                    endSessionLocked(expected)
                    throw SessionEnded()
                }
                throw retry
            }
        }
    }

    private suspend fun renewLocked(previous: AuthSession, expected: Long): AuthSession {
        ensureCurrent(expected)
        cooldownFailure(refreshRetryAt, refreshFailure)?.let { throw it }
        val response =
            try {
                apiService.renewSession(RefreshSessionRequestDto(previous.refreshToken))
            } catch (error: HttpException) {
                if (error.code() == 401) {
                    endSessionLocked(expected)
                    throw SessionEnded()
                }
                throw rememberRefreshFailure(error, expected)
            } catch (error: java.io.IOException) {
                throw rememberRefreshFailure(error, expected)
            }
        ensureCurrent(expected)
        val session = response.toSession(nowMillis())
        if (session.identity.userId != previous.identity.userId) {
            endSessionLocked(expected)
            throw SessionEnded()
        }
        saveSessionLocked(session, expected)
        refreshFailure = null
        refreshRetryAt = 0
        return session
    }

    private fun requireSession(expected: Long): AuthSession {
        ensureCurrent(expected)
        rejectedSession?.let { throw it }
        return readSessionLocked(expected)
            ?: run {
                publish(expected, SessionState.Unauthenticated)
                throw SessionEnded()
            }
    }

    override suspend fun <T> execute(request: (String) -> Call<T>): Result<Response<T>> {
        val expected = generation.get()
        return capture {
            var snapshot = sessionMutex.withLock {
                ensureCurrent(expected)
                when (val state = _sessionState.value) {
                    is SessionState.Authenticated -> Unit
                    is SessionState.AccessDenied -> throw state.failure
                    is SessionState.RecoverableError -> throw state.failure
                    else -> throw AuthFailure("IDENTITY_NOT_VERIFIED")
                }
                var session = requireSession(expected)
                val renewed = session.needsRenewal(nowMillis())
                if (renewed) session = renewLocked(session, expected)
                SessionSnapshot(session, renewed)
            }
            var mayReplay = false
            fun send(): Response<T> {
                val authorization = "Bearer ${snapshot.session.accessToken}"
                val call = request(authorization)
                val http = call.request()
                require(
                    (isProtectedApiRequest(http, apiBaseUrl) ||
                        profileBaseUrl?.let { isProtectedProfileRequest(http, it) } == true) &&
                        http.header("Authorization") == authorization,
                ) {
                    "SessionAccess requires a protected operation of the configured API."
                }
                mayReplay =
                    http.method() in setOf("GET", "HEAD") ||
                        (http.method() == "DELETE" && http.header("If-Match") == null)
                ensureCurrent(expected)
                val response = call.execute()
                if (generation.get() != expected) {
                    (response.body() as? okhttp3.ResponseBody)?.close()
                    response.errorBody()?.close()
                }
                ensureCurrent(expected)
                if (!response.isSuccessful) throw HttpException(response)
                return response
            }
            try {
                send()
            } catch (error: HttpException) {
                if (error.code() != 401 || !mayReplay) throw error
                snapshot = sessionMutex.withLock {
                    val current = requireSession(expected)
                    when {
                        !sameTokens(current, snapshot.session) -> SessionSnapshot(current, false)
                        snapshot.renewed -> {
                            endSessionLocked(expected)
                            throw SessionEnded()
                        }
                        else -> SessionSnapshot(renewLocked(current, expected), true)
                    }
                }
                try {
                    send()
                } catch (retry: HttpException) {
                    if (retry.code() == 401) {
                        sessionMutex.withLock {
                            val current = requireSession(expected)
                            if (sameTokens(current, snapshot.session)) endSessionLocked(expected)
                        }
                        throw SessionEnded()
                    }
                    throw retry
                }
            }
        }
    }

    private data class SessionSnapshot(val session: AuthSession, val renewed: Boolean)

    private fun sameTokens(left: AuthSession, right: AuthSession) =
        left.accessToken == right.accessToken && left.refreshToken == right.refreshToken

    // Never hold this fence across network I/O. It makes invalidation and publishing indivisible.
    private fun beginSessionChange(blockAccess: Boolean = false): Long =
        synchronized(sessionFence) {
            val next = generation.incrementAndGet()
            refreshFailure = null
            refreshRetryAt = 0
            identityFailure = null
            identityRetryAt = 0
            if (blockAccess && _sessionState.value !is SessionState.AccessDenied)
                _sessionState.value = SessionState.Checking
            next
        }

    private fun ensureCurrent(expected: Long) {
        if (generation.get() != expected) throw AuthFailure("SESSION_CHANGED")
    }

    private fun publish(expected: Long, state: SessionState) =
        synchronized(sessionFence) {
            ensureCurrent(expected)
            _sessionState.value = state
        }

    private fun endSessionLocked(expected: Long) =
        synchronized(sessionFence) {
            ensureCurrent(expected)
            clearSessionLocked(beginSessionChange(blockAccess = true))
        }

    private fun readSessionLocked(expected: Long): AuthSession? {
        ensureCurrent(expected)
        return try {
            secureStorage.getSession()
        } catch (error: Exception) {
            val failure = storageFailure(error, StorageAction.READ)
            publish(expected, SessionState.RecoverableError(failure))
            throw failure
        }
    }

    private fun saveSessionLocked(session: AuthSession, expected: Long) =
        synchronized(sessionFence) {
            ensureCurrent(expected)
            try {
                secureStorage.saveSession(session)
                revision.incrementAndGet()
                rejectedSession = null
            } catch (error: Exception) {
                val failure = storageFailure(error, StorageAction.SAVE)
                // A failed write may have left old credentials behind. Explicit cleanup is
                // required.
                rejectedSession = failure
                generation.incrementAndGet()
                _sessionState.value = SessionState.AccessDenied(failure)
                throw failure
            }
        }

    private fun failure(error: Exception) = mapAuthError(error, nowMillis())

    private fun retryDeadline(failure: AuthFailure): Long {
        val now = nowMillis()
        return now + (failure.retryAfterMillis ?: 0).coerceIn(0, Long.MAX_VALUE - now)
    }

    private fun cooldownFailure(deadline: Long, failure: AuthFailure?): AuthFailure? {
        val remaining = deadline - nowMillis()
        if (remaining <= 0 || failure == null) return null
        return AuthFailure(
            failure.code,
            failure.status,
            failure.validationErrors,
            failure.traceId,
            failure.validationError,
            failure.storageAction,
            remaining,
        )
    }

    private fun rememberRefreshFailure(error: Exception, expected: Long): AuthFailure =
        synchronized(sessionFence) {
            ensureCurrent(expected)
            val failure = failure(error)
            refreshFailure = failure
            refreshRetryAt = retryDeadline(failure)
            publish(expected, SessionState.RecoverableError(failure))
            failure
        }

    private fun validateEmail(email: String) {
        AuthValidator.emailError(email)?.let { invalid(it) }
    }

    private fun invalid(error: AuthValidationError): Nothing =
        throw AuthFailure("VALIDATION_ERROR", validationError = error)

    private fun Response<Unit>.requireSuccess() {
        if (!isSuccessful) throw HttpException(this)
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (error: CancellationException) {
                throw error
            } catch (_: SessionEnded) {
                Result.failure(AuthFailure("SESSION_ENDED"))
            } catch (error: Exception) {
                Result.failure(failure(error))
            }
        }

    private class SessionEnded : Exception()
}
