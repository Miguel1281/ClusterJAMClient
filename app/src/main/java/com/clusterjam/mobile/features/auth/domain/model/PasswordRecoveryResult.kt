package com.clusterjam.mobile.features.auth.domain.model

/** The server confirmed the password change; a cleanup failure must never repeat the OTP. */
data class PasswordRecoveryResult(val cleanupFailure: AuthFailure? = null)
