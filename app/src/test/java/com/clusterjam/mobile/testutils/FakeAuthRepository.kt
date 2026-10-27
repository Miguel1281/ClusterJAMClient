package com.clusterjam.mobile.testutils

import com.clusterjam.mobile.features.auth.domain.model.*
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository : AuthRepository {
    val states = MutableStateFlow<SessionState>(SessionState.Checking)
    override val sessionState = states.asStateFlow()
    var restore: suspend () -> Result<VerifiedIdentity?> = {
        states.value = SessionState.Unauthenticated
        Result.success(null)
    }
    var loginAction: suspend (AuthCredentials) -> Result<VerifiedIdentity> = {
        Result.failure(AuthFailure("INVALID_CREDENTIALS"))
    }
    var registerAction: suspend (AuthCredentials) -> Result<RegistrationResult> = {
        Result.failure(AuthFailure("UNEXPECTED_ERROR"))
    }
    var confirmationAction: suspend (String, String) -> Result<Unit> = { _, _ ->
        Result.success(Unit)
    }
    var confirmationRequestAction: suspend (String) -> Result<EmailConfirmationRequestResult> = {
        Result.success(EmailConfirmationRequestResult())
    }
    var logoutAction: suspend () -> Result<Unit> = {
        states.value = SessionState.Unauthenticated
        Result.success(Unit)
    }

    override fun invalidateIdentity() {
        if (states.value is SessionState.Authenticated) states.value = SessionState.Checking
    }

    override suspend fun login(credentials: AuthCredentials) = loginAction(credentials)

    override suspend fun restoreSession() = restore()

    override suspend fun currentIdentity(): Result<VerifiedIdentity> =
        restore().map { requireNotNull(it) }

    override suspend fun logout() = logoutAction()

    override suspend fun discardSession(): Result<Unit> {
        states.value = SessionState.Unauthenticated
        return Result.success(Unit)
    }

    override suspend fun renewSession(): Result<AuthSession> =
        Result.failure(AuthFailure("SESSION_ENDED"))

    override suspend fun register(credentials: AuthCredentials): Result<RegistrationResult> =
        registerAction(credentials)

    override suspend fun confirmEmail(email: String, code: String) = confirmationAction(email, code)

    override suspend fun requestEmailConfirmation(email: String) = confirmationRequestAction(email)

    override suspend fun requestPasswordRecovery(email: String) = Result.success(Unit)

    override suspend fun recoverPassword(email: String, code: String, newPassword: String) =
        discardSession()
}
