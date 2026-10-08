package com.example.cluster_jam_client.data.auth

import com.example.cluster_jam_client.domain.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            if (request.password.isEmpty()) invalid("Ingresa tu contraseña.")
            val session = apiService.login(request).toSession()
            secureStorage.saveSession(session)
            session
        }
    }

    override suspend fun register(credentials: AuthCredentials): Result<RegistrationResult> = capture {
        val request = credentials.copy(email = credentials.email.trim())
        validateEmail(request.email)
        AuthValidation.passwordError(request.password)?.let { invalid(it) }
        val response = apiService.register(request)
        val status = RegistrationStatus.values().find { it.name == response.status }
            ?: invalidResponse()
        RegistrationResult(identity(response.userId, response.email), status)
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
        val response = apiService.requestPasswordRecovery(PasswordRecoveryRequest(normalized))
        if (!response.isSuccessful) throw HttpException(response)
        Unit
    }

    override suspend fun recoverPassword(email: String, code: String, newPassword: String): Result<Unit> = capture {
        sessionMutex.withLock {
            val normalized = email.trim()
            validateEmail(normalized)
            AuthValidation.codeError(code)?.let { invalid(it) }
            AuthValidation.passwordError(newPassword)?.let { invalid(it) }
            apiService.recoverPassword(RecoverPasswordRequest(normalized, code, newPassword)).requireSuccess()
            secureStorage.clear()
        }
    }

    override suspend fun checkHealth(): Result<ServiceHealth> = capture {
        val live = apiService.liveness().status == "ok"
        val ready = apiService.readiness()
        if (!ready.isSuccessful && ready.code() != 503) throw HttpException(ready)
        ServiceHealth(live, ready.isSuccessful && ready.body()?.status == "ok")
    }

    private suspend fun readIdentityLocked(): AuthIdentity {
        val response = authorizedLocked { apiService.currentIdentity(it) }
        val current = identity(response.userId, response.email)
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
            apiService.renewSession(RefreshSessionRequest(previous.refreshToken))
        } catch (error: HttpException) {
            if (error.code() == 401) {
                secureStorage.clear()
                throw SessionEnded()
            }
            throw error
        }
        val session = response.toSession()
        if (session.identity.userId != previous.identity.userId) {
            secureStorage.clear()
            throw SessionEnded()
        }
        secureStorage.saveSession(session)
        return session
    }

    private fun requireSession(): AuthSession = secureStorage.getSession() ?: throw SessionEnded()

    private fun SessionResponse.toSession(): AuthSession {
        val access = accessToken?.takeIf { it.isNotBlank() && !it.any(Char::isWhitespace) } ?: invalidResponse()
        val refresh = refreshToken?.takeIf { it.isNotBlank() && it.length <= 4096 && !it.any(Char::isWhitespace) } ?: invalidResponse()
        val lifetime = expiresIn?.takeIf { it > 0 && it <= (Long.MAX_VALUE - nowMillis()) / 1000 } ?: invalidResponse()
        if (!tokenType.equals("bearer", ignoreCase = true)) invalidResponse()
        return AuthSession(identity(userId, email), access, refresh, nowMillis() + lifetime * 1000)
    }

    private fun identity(userId: String?, email: String?): AuthIdentity = AuthIdentity(
        userId?.takeIf { it.isNotBlank() } ?: invalidResponse(),
        email?.takeIf { it.isNotBlank() } ?: invalidResponse()
    )

    private fun validateEmail(email: String) { AuthValidation.emailError(email)?.let { invalid(it) } }
    private fun invalid(message: String): Nothing = throw AuthFailure("VALIDATION_ERROR", message)
    private fun invalidResponse(): Nothing = throw AuthFailure("INVALID_RESPONSE", "El servicio devolvió una respuesta inválida.")

    private fun Response<Unit>.requireSuccess() {
        if (!isSuccessful) throw HttpException(this)
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) }
        catch (error: CancellationException) { throw error }
        catch (_: SessionEnded) { Result.failure(AuthFailure("SESSION_ENDED", "Tu sesión terminó. Inicia sesión nuevamente.")) }
        catch (error: Exception) { Result.failure(mapAuthError(error)) }
    }

    private class SessionEnded : Exception()
}
