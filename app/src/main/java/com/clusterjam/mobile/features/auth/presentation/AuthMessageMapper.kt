package com.clusterjam.mobile.features.auth.presentation

import android.content.Context
import androidx.annotation.StringRes
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.StorageAction
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidationError

data class AuthMessage(
    @param:StringRes val resourceId: Int,
    val arguments: List<Any> = emptyList(),
) {
    fun resolve(context: Context): String = context.getString(resourceId, *arguments.toTypedArray())
}

object AuthMessageMapper {
    fun failure(error: Throwable): AuthMessage {
        val failure = error as? AuthFailure ?: return AuthMessage(R.string.auth_error_unexpected)
        val resource =
            when (failure.code) {
                "NETWORK_ERROR" -> R.string.auth_error_network
                "INVALID_RESPONSE" -> R.string.auth_error_invalid_response
                "CLIENT_APP_ACCESS_DENIED" -> R.string.auth_error_client_access_denied
                "INVALID_APP_ROLE",
                "INVALID_IDENTITY_ROLE" -> R.string.auth_error_identity_role
                "IDENTITY_MISMATCH" -> R.string.auth_error_identity_mismatch
                "INVALID_CREDENTIALS" -> R.string.auth_error_credentials
                "EMAIL_NOT_CONFIRMED" -> R.string.auth_error_email_not_confirmed
                "EMAIL_ALREADY_REGISTERED" -> R.string.auth_error_email_registered
                "INVALID_EMAIL" -> R.string.auth_error_invalid_email
                "WEAK_PASSWORD" -> R.string.auth_error_weak_password
                "INVALID_RECOVERY_CODE" -> R.string.auth_error_recovery_code
                "SAME_PASSWORD" -> R.string.auth_error_same_password
                "PASSWORD_RECOVERY_NOT_ALLOWED" -> R.string.auth_error_recovery_not_allowed
                "SESSION_REFRESH_CONFLICT" -> R.string.auth_error_refresh_conflict
                "INVALID_ACCESS_TOKEN",
                "INVALID_REFRESH_TOKEN",
                "SESSION_ENDED" -> R.string.auth_error_session_ended
                "VALIDATION_ERROR" ->
                    failure.validationError?.let(::validation) ?: R.string.auth_error_validation
                "INVALID_REQUEST",
                "UNPROCESSABLE_ENTITY" -> R.string.auth_error_validation
                "STORAGE_ERROR" ->
                    when (failure.storageAction) {
                        StorageAction.CLEAR -> R.string.auth_error_storage_clear
                        else -> R.string.auth_error_storage_save
                    }
                else -> return httpFailure(failure.status)
            }
        return AuthMessage(resource)
    }

    @StringRes
    fun validation(error: AuthValidationError): Int =
        when (error) {
            AuthValidationError.INVALID_EMAIL -> R.string.auth_validation_email
            AuthValidationError.PASSWORD_REQUIRED -> R.string.auth_validation_password_required
            AuthValidationError.PASSWORD_TOO_SHORT -> R.string.auth_validation_password_short
            AuthValidationError.INVALID_CODE -> R.string.auth_validation_code
            AuthValidationError.RECOVERY_CODE_REQUIRED -> R.string.auth_validation_request_code
        }

    private fun httpFailure(status: Int?): AuthMessage {
        val resource =
            when (status) {
                null -> R.string.auth_error_unexpected
                401 -> R.string.auth_error_unauthorized
                403 -> R.string.auth_error_forbidden
                404 -> R.string.auth_error_not_found
                409 -> R.string.auth_error_conflict
                422 -> R.string.auth_error_unprocessable
                429 -> R.string.auth_error_rate_limit
                500,
                502,
                503,
                504 -> R.string.auth_error_unavailable
                else -> return AuthMessage(R.string.auth_error_http, listOf(status))
            }
        return AuthMessage(resource)
    }
}
