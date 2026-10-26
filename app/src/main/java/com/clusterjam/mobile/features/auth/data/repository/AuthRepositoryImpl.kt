package com.clusterjam.mobile.features.auth.data.repository

import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.mapper.mapAuthError
import com.clusterjam.mobile.features.auth.data.mapper.toIdentity
import com.clusterjam.mobile.features.auth.data.mapper.toRegistration
import com.clusterjam.mobile.features.auth.data.mapper.toSession
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
import com.clusterjam.mobile.features.auth.data.remote.dto.LoginRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.PasswordRecoveryRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RecoverPasswordRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RefreshSessionRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RegistrationRequestDto
import com.clusterjam.mobile.features.auth.domain.model.AuthCredentials
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.AuthIdentity
import com.clusterjam.mobile.features.auth.domain.model.AuthSession
import com.clusterjam.mobile.features.auth.domain.model.RegistrationResult
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidationError
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

class AuthRepositoryImpl(
    private val apiService: AuthApiService,
    private val secureStorage: SecureStorage,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : AuthRepository {
    private val sessionMutex = Mutex()

    override suspend fun login(credentials: AuthCredentials): Result<AuthSession> = capture {
        sessionMutex.withLock {
            val request = credentials.copy(email = credentials.email.trim())
            validateEmail(request.email)
            if (request.password.isEmpty()) invalid(AuthValidationError.PASSWORD_REQUIRED)
            val session = apiService.login(LoginRequestDto(request.email, request.password))
                .toSession(nowMillis())
            secureStorage.saveSession(session)
            session
        }
    }

    override suspend fun register(
        credentials: AuthCredentials
    ): Result<RegistrationResult> = capture {
        val request = credentials.copy(email = credentials.email.trim())
        validateEmail(request.email)
        AuthValidator.passwordError(request.password)?.let { invalid(it) }
        apiService.register(RegistrationRequestDto(request.email, request.password))
            .toRegistration()
    }

    override suspend fun restoreSession(): Result<AuthIdentity?> = capture {
        sessionMutex.withLock {
            if (secureStorage.getSession() == null) return@withLock null
            try {
                readIdentityLocked()
            } catch (_: SessionEnded) {
                null
            }
        }
    }

    override suspend fun currentIdentity(): Result<AuthIdentity> = capture {
        sessionMutex.withLock { readIdentityLocked() }
    }

    override suspend fun renewSession(): Result<AuthSession> = capture {
        sessionMutex.withLock { renewLocked(requireSession()) }
    }

    override suspend fun logout(): Result<Unit> = capture {
        sessionMutex.withLock {
            try {
                if (secureStorage.getSession() != null) {
                    try {
                        authorizedLocked { token -> apiService.logout(token).requireSuccess() }
                    } catch (_: SessionEnded) {
                        // A definitively rejected session has already been discarded.
                    }
                }
            } finally {
                secureStorage.clear()
            }
        }
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
        newPassword: String
    ): Result<Unit> = capture {
        sessionMutex.withLock {
            val normalized = email.trim()
            validateEmail(normalized)
            AuthValidator.codeError(code)?.let { invalid(it) }
            AuthValidator.passwordError(newPassword)?.let { invalid(it) }
            apiService.recoverPassword(RecoverPasswordRequestDto(normalized, code, newPassword))
                .requireSuccess()
            secureStorage.clear()
        }
    }

    private suspend fun readIdentityLocked(): AuthIdentity {
        val response = authorizedLocked { apiService.currentIdentity(it) }
        val current = response.toIdentity()
        val session = requireSession()
        if (current.userId != session.identity.userId) {
            secureStorage.clear()
            throw SessionEnded()
        }
        secureStorage.saveSession(session.copy(identity = current))
        return current
    }

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
                    secureStorage.clear()
                    throw SessionEnded()
                }
                throw retry
            }
        }
    }

    private suspend fun renewLocked(previous: AuthSession): AuthSession {
        val response = try {
            apiService.renewSession(RefreshSessionRequestDto(previous.refreshToken))
        } catch (error: HttpException) {
            if (error.code() == 401) {
                secureStorage.clear()
                throw SessionEnded()
            }
            throw error
        }
        val session = response.toSession(nowMillis())
        if (session.identity.userId != previous.identity.userId) {
            secureStorage.clear()
            throw SessionEnded()
        }
        secureStorage.saveSession(session)
        return session
    }

    private fun requireSession(): AuthSession = secureStorage.getSession() ?: throw SessionEnded()

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
