package com.example.cluster_jam_client.domain.auth

data class AuthIdentity(val userId: String, val email: String)

data class AuthSession(
    val identity: AuthIdentity,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
) {
    fun needsRenewal(nowMillis: Long): Boolean = expiresAtMillis <= nowMillis + 30_000L
}

enum class RegistrationStatus { PENDING_EMAIL_CONFIRMATION, ACTIVE }

data class RegistrationResult(val identity: AuthIdentity, val status: RegistrationStatus)
data class ServiceHealth(val live: Boolean, val ready: Boolean)

class AuthFailure(
    val code: String,
    override val message: String,
    val status: Int? = null
) : Exception(message)

object AuthValidation {
    fun emailError(email: String): String? = when {
        email.length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email) ->
            "Ingresa un correo válido (máximo 254 caracteres)."
        else -> null
    }
    fun passwordError(password: String): String? =
        if (password.length < 8) "La contraseña debe tener al menos 8 caracteres." else null
    fun codeError(code: String): String? =
        if (!Regex("^[0-9]{6}$").matches(code)) "Ingresa el código de seis dígitos." else null
}
