package com.clusterjam.mobile.domain.auth

interface AuthRepository {
    suspend fun login(credentials: AuthCredentials): Result<AuthSession>
    suspend fun register(credentials: AuthCredentials): Result<RegistrationResult>
    suspend fun restoreSession(): Result<AuthIdentity?>
    suspend fun currentIdentity(): Result<AuthIdentity>
    suspend fun renewSession(): Result<AuthSession>
    suspend fun logout(): Result<Unit>
    suspend fun requestPasswordRecovery(email: String): Result<Unit>
    suspend fun recoverPassword(email: String, code: String, newPassword: String): Result<Unit>
    suspend fun checkHealth(): Result<ServiceHealth>
}
