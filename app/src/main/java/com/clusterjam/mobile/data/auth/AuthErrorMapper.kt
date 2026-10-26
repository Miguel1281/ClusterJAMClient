package com.clusterjam.mobile.data.auth

import com.clusterjam.mobile.domain.auth.AuthFailure
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

internal data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val errors: List<String?>? = null,
    val traceId: String? = null
)

internal fun mapAuthError(error: Exception): AuthFailure {
    if (error is AuthFailure) return error
    if (error is IOException) {
        return AuthFailure(
            "NETWORK_ERROR",
            "No se pudo conectar. Verifica tu conexión y vuelve a intentar."
        )
    }
    if (error !is HttpException) {
        return AuthFailure(
            "UNEXPECTED_ERROR",
            "No se pudo completar la operación. Vuelve a intentar."
        )
    }
    val status = error.code()
    val problem = try {
        error.response()?.errorBody()?.use { body ->
            Gson().fromJson(body.string(), ProblemDetails::class.java)
        }
    } catch (_: Exception) {
        null
    }
    val code = problem?.code?.takeIf { it.isNotBlank() }
    val message = when (code) {
        "INVALID_CREDENTIALS" -> "Correo o contraseña incorrectos."
        "EMAIL_NOT_CONFIRMED" -> "Confirma tu correo antes de iniciar sesión."
        "EMAIL_ALREADY_REGISTERED" -> "El correo ya está registrado."
        "INVALID_EMAIL" -> "El correo no es válido."
        "WEAK_PASSWORD" -> "La contraseña no cumple la política de seguridad."
        "INVALID_RECOVERY_CODE" ->
            "El código es inválido, expiró o ya fue utilizado. Solicita otro."
        "SAME_PASSWORD" -> "Usa una contraseña diferente de la actual y solicita un código nuevo."
        "PASSWORD_RECOVERY_NOT_ALLOWED" ->
            "No se puede recuperar esta cuenta. Contacta al soporte."
        "SESSION_REFRESH_CONFLICT" -> "La sesión se está renovando. Vuelve a intentar."
        "INVALID_ACCESS_TOKEN", "INVALID_REFRESH_TOKEN" ->
            "Tu sesión terminó. Inicia sesión nuevamente."
        "VALIDATION_ERROR", "INVALID_REQUEST", "UNPROCESSABLE_ENTITY" ->
            "Revisa los datos ingresados y vuelve a intentar."
        else -> when (status) {
            401 -> "La autorización es inválida o expiró."
            403 -> "No tienes permiso para realizar esta operación."
            404 -> "No se encontró el servicio. Revisa la dirección del API."
            409 -> "La solicitud entró en conflicto. Vuelve a intentar."
            422 -> "Revisa el correo, el código y la contraseña."
            429 -> "Demasiados intentos. Espera antes de volver a intentar."
            500, 502, 503, 504 -> "El servicio no está disponible. Intenta más tarde."
            else -> "No se pudo completar la operación (HTTP $status)."
        }
    }
    return AuthFailure(
        code = code ?: "HTTP_$status",
        message = message,
        status = status,
        validationErrors = problem?.errors.orEmpty().filterNotNull().filter { it.isNotBlank() },
        traceId = problem?.traceId?.takeIf { it.isNotBlank() }
    )
}
