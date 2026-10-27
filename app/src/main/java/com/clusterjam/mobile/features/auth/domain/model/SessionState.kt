package com.clusterjam.mobile.features.auth.domain.model

sealed interface SessionState {
    data object Checking : SessionState

    data object Unauthenticated : SessionState

    data class Authenticated(val identity: VerifiedIdentity) : SessionState {
        init {
            require(identity.appRole == AppRole.CLIENT)
        }
    }

    data class AccessDenied(
        val failure: AuthFailure,
        val cleanupFailure: AuthFailure? = null,
    ) : SessionState

    data class RecoverableError(val failure: AuthFailure) : SessionState
}
