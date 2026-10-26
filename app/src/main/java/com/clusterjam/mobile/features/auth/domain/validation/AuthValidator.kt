package com.clusterjam.mobile.features.auth.domain.validation

enum class AuthValidationError {
    INVALID_EMAIL,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    INVALID_CODE,
    RECOVERY_CODE_REQUIRED
}

object AuthValidator {
    fun emailError(email: String): AuthValidationError? =
        if (email.length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) {
            AuthValidationError.INVALID_EMAIL
        } else {
            null
        }

    fun passwordError(password: String): AuthValidationError? =
        if (password.length < 8) AuthValidationError.PASSWORD_TOO_SHORT else null

    fun codeError(code: String): AuthValidationError? =
        if (!Regex("^[0-9]{6}$").matches(code)) AuthValidationError.INVALID_CODE else null
}
