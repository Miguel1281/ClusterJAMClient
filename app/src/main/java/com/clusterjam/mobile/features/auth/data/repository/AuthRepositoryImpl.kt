package com.clusterjam.mobile.features.auth.data.repository

import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.mapper.mapAuthError
import com.clusterjam.mobile.features.auth.data.mapper.toIdentity
import com.clusterjam.mobile.features.auth.data.mapper.toRegistration
import com.clusterjam.mobile.features.auth.data.mapper.toSession
import com.clusterjam.mobile.features.auth.data.mapper.toVerifiedIdentity
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
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
import com.clusterjam.mobile.features.auth.domain.model.RegistrationResult
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.model.StorageAction
import com.clusterjam.mobile.features.auth.domain.model.VerifiedIdentity
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidationError
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

class AuthRepositoryImpl(
    private val apiService: AuthApiService,
    private val secureStorage: SecureStorage,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : AuthRepository {
    private val sessionMutex = Mutex()
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Checking)
    override val sessionState = _sessionState.asStateFlow()
    private var rejectedSession: AuthFailure? = null

    override fun invalidateIdentity() {
        _sessionState.update { if (it is SessionState.Authenticated) SessionState.Checking else it }
    }

    override suspend fun login(credentials: AuthCredentials): Result<VerifiedIdentity> = capture {
        sessionMutex.withLock {
            val request = credentials.copy(email = credentials.email.trim())
            validateEmail(request.email)
            if (request.password.isEmpty()) invalid(AuthValidationError.PASSWORD_REQUIRED)
            if (rejectedSession != null) clearSessionLocked()
            val session =
                apiService
                    .login(LoginRequestDto(request.email, request.password))
                    .toSession(nowMillis())
            establishSessionLocked(session)
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

    override suspend fun restoreSession(): Result<VerifiedIdentity?> = capture {
        sessionMutex.withLock {
            rejectedSession?.let { throw it }
            _sessionState.value = SessionState.Checking
            try {
                if (secureStorage.getSession() == null) {
                    _sessionState.value = SessionState.Unauthenticated
                    return@withLock null
                }
                readIdentityLocked()
            } catch (_: SessionEnded) {
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val failure = mapAuthError(error)
                if (_sessionState.value !is SessionState.AccessDenied) {
                    _sessionState.value = SessionState.RecoverableError(failure)
                }
                throw failure
            }
        }
    }

    override suspend fun currentIdentity(): Result<VerifiedIdentity> = capture {
        sessionMutex.withLock { readIdentityLocked() }
    }

    override suspend fun renewSession(): Result<AuthSession> = capture {
        sessionMutex.withLock { renewLocked(requireSession()) }
    }

    override suspend fun logout(): Result<Unit> = capture {
        sessionMutex.withLock {
            val previouslyRejected = rejectedSession != null
            if (!previouslyRejected) _sessionState.value = SessionState.Checking
            try {
                if (!previouslyRejected && secureStorage.getSession() != null) {
                    try {
                        authorizedLocked { token -> apiService.logout(token).requireSuccess() }
                    } catch (_: SessionEnded) {
                        // Local cleanup still runs for an expired or rejected session.
                    }
                }
            } finally {
                clearSessionLocked()
            }
        }
    }

    override suspend fun discardSession(): Result<Unit> = capture {
        sessionMutex.withLock { clearSessionLocked() }
    }

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> = capture {
        val normalized = email.trim()
        validateEmail(normalized)
        val response = apiService.requestPasswordRecovery(PasswordRecoveryRequestDto(normalized))
        if (!response.isSuccessful) throw HttpException(response)
        Unit
    }

    override suspend fun recoverPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = capture {
        sessionMutex.withLock {
            val normalized = email.trim()
            validateEmail(normalized)
            AuthValidator.codeError(code)?.let { invalid(it) }
            AuthValidator.passwordError(newPassword)?.let { invalid(it) }
            apiService
                .recoverPassword(RecoverPasswordRequestDto(normalized, code, newPassword))
                .requireSuccess()
            clearSessionLocked()
        }
    }

    // Login and future email confirmation both establish tokens, then use this verification.
    private suspend fun establishSessionLocked(session: AuthSession): VerifiedIdentity {
        secureStorage.saveSession(session)
        rejectedSession = null
        return readIdentityLocked()
    }

    private suspend fun readIdentityLocked(): VerifiedIdentity {
        rejectedSession?.let { throw it }
        _sessionState.value = SessionState.Checking
        try {
            val response = authorizedLocked { apiService.currentIdentity(it) }
            val session = requireSession()
            if (response.toIdentity().userId != session.identity.userId) {
                throw AuthFailure("IDENTITY_MISMATCH")
            }
            val current = response.toVerifiedIdentity()
            if (current.appRole == AppRole.ADMINISTRATOR) {
                val failure = AuthFailure("CLIENT_APP_ACCESS_DENIED")
                denyAccessLocked(failure)
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
            secureStorage.saveSession(
                session.copy(identity = AuthIdentity(current.userId, current.email)),
            )
            _sessionState.value = SessionState.Authenticated(current)
            return current
        } catch (error: CancellationException) {
            throw error
        } catch (_: SessionEnded) {
            throw SessionEnded()
        } catch (error: Exception) {
            val failure = mapAuthError(error)
            if (_sessionState.value !is SessionState.AccessDenied) {
                if (
                    failure.code in
                        setOf("INVALID_IDENTITY_ROLE", "INVALID_APP_ROLE", "IDENTITY_MISMATCH") ||
                        failure.status == 403
                ) {
                    denyAccessLocked(failure)
                } else {
                    _sessionState.value = SessionState.RecoverableError(failure)
                }
            }
            throw failure
        }
    }

    private fun denyAccessLocked(failure: AuthFailure) {
        rejectedSession = failure
        _sessionState.value = SessionState.AccessDenied(failure)
        try {
            secureStorage.clear()
        } catch (error: Exception) {
            _sessionState.value = SessionState.AccessDenied(failure, storageFailure(error))
        }
    }

    private fun clearSessionLocked() {
        try {
            secureStorage.clear()
            rejectedSession = null
            _sessionState.value = SessionState.Unauthenticated
        } catch (error: Exception) {
            val failure = storageFailure(error)
            rejectedSession = rejectedSession ?: failure
            _sessionState.value =
                SessionState.AccessDenied(requireNotNull(rejectedSession), failure)
            throw failure
        }
    }

    private fun storageFailure(error: Exception): AuthFailure =
        (error as? AuthFailure) ?: AuthFailure("STORAGE_ERROR", storageAction = StorageAction.CLEAR)

    private suspend fun <T> authorizedLocked(request: suspend (String) -> T): T {
        var session = requireSession()
        if (session.needsRenewal(nowMillis())) session = renewLocked(session)
        return try {
            request("Bearer ${session.accessToken}")
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            session = renewLocked(session)
            try {
                request("Bearer ${session.accessToken}")
            } catch (retry: HttpException) {
                if (retry.code() == 401) {
                    clearSessionLocked()
                    throw SessionEnded()
                }
                throw retry
            }
        }
    }

    private suspend fun renewLocked(previous: AuthSession): AuthSession {
        val response =
            try {
                apiService.renewSession(RefreshSessionRequestDto(previous.refreshToken))
            } catch (error: HttpException) {
                if (error.code() == 401) {
                    clearSessionLocked()
                    throw SessionEnded()
                }
                throw error
            }
        val session = response.toSession(nowMillis())
        if (session.identity.userId != previous.identity.userId) {
            clearSessionLocked()
            throw SessionEnded()
        }
        secureStorage.saveSession(session)
        return session
    }

    private fun requireSession(): AuthSession {
        rejectedSession?.let { throw it }
        return secureStorage.getSession()
            ?: run {
                _sessionState.value = SessionState.Unauthenticated
                throw SessionEnded()
            }
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
                Result.failure(mapAuthError(error))
            }
        }

    private class SessionEnded : Exception()
}
