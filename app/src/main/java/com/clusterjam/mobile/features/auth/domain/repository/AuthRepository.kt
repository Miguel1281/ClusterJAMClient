package com.clusterjam.mobile.features.auth.domain.repository

import com.clusterjam.mobile.features.auth.domain.model.AuthCredentials
import com.clusterjam.mobile.features.auth.domain.model.AuthIdentity
import com.clusterjam.mobile.features.auth.domain.model.AuthSession
import com.clusterjam.mobile.features.auth.domain.model.RegistrationResult

interface AuthRepository {
    suspend fun login(credentials: AuthCredentials): Result<AuthSession>
    suspend fun register(credentials: AuthCredentials): Result<RegistrationResult>
    suspend fun restoreSession(): Result<AuthIdentity?>
    suspend fun currentIdentity(): Result<AuthIdentity>
    suspend fun renewSession(): Result<AuthSession>
    suspend fun logout(): Result<Unit>
    suspend fun requestPasswordRecovery(email: String): Result<Unit>
    suspend fun recoverPassword(email: String, code: String, newPassword: String): Result<Unit>
}
