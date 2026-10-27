package com.clusterjam.mobile.features.auth.domain.repository

import com.clusterjam.mobile.features.auth.domain.model.AuthCredentials
import com.clusterjam.mobile.features.auth.domain.model.AuthSession
import com.clusterjam.mobile.features.auth.domain.model.EmailConfirmationRequestResult
import com.clusterjam.mobile.features.auth.domain.model.RegistrationResult
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.model.VerifiedIdentity
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionState>

    fun invalidateIdentity()

    suspend fun login(credentials: AuthCredentials): Result<VerifiedIdentity>

    suspend fun register(credentials: AuthCredentials): Result<RegistrationResult>

    // Success means the confirmation POST completed; sessionState determines access.
    suspend fun confirmEmail(email: String, code: String): Result<Unit>

    suspend fun requestEmailConfirmation(email: String): Result<EmailConfirmationRequestResult>

    suspend fun restoreSession(): Result<VerifiedIdentity?>

    suspend fun currentIdentity(): Result<VerifiedIdentity>

    suspend fun renewSession(): Result<AuthSession>

    suspend fun logout(): Result<Unit>

    suspend fun discardSession(): Result<Unit>

    suspend fun requestPasswordRecovery(email: String): Result<Unit>

    suspend fun recoverPassword(email: String, code: String, newPassword: String): Result<Unit>
}
