package com.clusterjam.mobile.features.auth.presentation

import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.StorageAction
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidationError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthMessageMapperTest {
    @Test
    fun knownBusinessCodesUseLocalResources() {
        val messages =
            mapOf(
                "INVALID_CREDENTIALS" to R.string.auth_error_credentials,
                "CLIENT_APP_ACCESS_DENIED" to R.string.auth_error_client_access_denied,
                "INVALID_APP_ROLE" to R.string.auth_error_identity_role,
                "INVALID_IDENTITY_ROLE" to R.string.auth_error_identity_role,
                "IDENTITY_MISMATCH" to R.string.auth_error_identity_mismatch,
                "EMAIL_NOT_CONFIRMED" to R.string.auth_error_email_not_confirmed,
                "EMAIL_ALREADY_REGISTERED" to R.string.auth_error_email_registered,
                "INVALID_EMAIL" to R.string.auth_error_invalid_email,
                "WEAK_PASSWORD" to R.string.auth_error_weak_password,
                "INVALID_RECOVERY_CODE" to R.string.auth_error_recovery_code,
                "SAME_PASSWORD" to R.string.auth_error_same_password,
                "PASSWORD_RECOVERY_NOT_ALLOWED" to R.string.auth_error_recovery_not_allowed,
                "SESSION_REFRESH_CONFLICT" to R.string.auth_error_refresh_conflict,
                "INVALID_ACCESS_TOKEN" to R.string.auth_error_session_ended,
                "INVALID_REFRESH_TOKEN" to R.string.auth_error_session_ended,
                "SESSION_ENDED" to R.string.auth_error_session_ended,
                "VALIDATION_ERROR" to R.string.auth_error_validation,
                "INVALID_REQUEST" to R.string.auth_error_validation,
                "UNPROCESSABLE_ENTITY" to R.string.auth_error_validation,
            )
        for ((code, resource) in messages) {
            val message = AuthMessageMapper.failure(AuthFailure(code))
            assertEquals(AuthMessage(resource), message)
        }
    }

    @Test
    fun unknownCodeFallsBackToActualHttpStatus() {
        val resources =
            mapOf(
                401 to R.string.auth_error_unauthorized,
                403 to R.string.auth_error_forbidden,
                404 to R.string.auth_error_not_found,
                409 to R.string.auth_error_conflict,
                422 to R.string.auth_error_unprocessable,
                429 to R.string.auth_error_rate_limit,
                500 to R.string.auth_error_unavailable,
                502 to R.string.auth_error_unavailable,
                503 to R.string.auth_error_unavailable,
                504 to R.string.auth_error_unavailable,
            )
        for ((status, resource) in resources) {
            assertEquals(
                AuthMessage(resource),
                AuthMessageMapper.failure(AuthFailure("FUTURE_CODE", status = status)),
            )
        }
        assertEquals(
            AuthMessage(R.string.auth_error_http, listOf(418)),
            AuthMessageMapper.failure(AuthFailure("FUTURE_CODE", status = 418)),
        )
    }

    @Test
    fun remoteValidationDiagnosticsAreNotShownToUser() {
        val failure =
            AuthFailure(
                "VALIDATION_ERROR",
                status = 422,
                validationErrors = listOf("Private provider diagnostic"),
                traceId = "trace-id",
            )
        assertEquals(
            AuthMessage(R.string.auth_error_validation),
            AuthMessageMapper.failure(failure),
        )
    }

    @Test
    fun localValidationSelectsSpecificMessage() {
        val resources =
            mapOf(
                AuthValidationError.INVALID_EMAIL to R.string.auth_validation_email,
                AuthValidationError.PASSWORD_REQUIRED to R.string.auth_validation_password_required,
                AuthValidationError.PASSWORD_TOO_SHORT to R.string.auth_validation_password_short,
                AuthValidationError.INVALID_CODE to R.string.auth_validation_code,
                AuthValidationError.RECOVERY_CODE_REQUIRED to R.string.auth_validation_request_code,
            )
        for ((error, resource) in resources) {
            assertEquals(resource, AuthMessageMapper.validation(error))
            assertEquals(
                AuthMessage(resource),
                AuthMessageMapper.failure(AuthFailure("VALIDATION_ERROR", validationError = error)),
            )
        }
    }

    @Test
    fun networkInvalidResponseAndUnexpectedFailuresUseLocalMessages() {
        assertEquals(
            AuthMessage(R.string.auth_error_network),
            AuthMessageMapper.failure(AuthFailure("NETWORK_ERROR")),
        )
        assertEquals(
            AuthMessage(R.string.auth_error_invalid_response),
            AuthMessageMapper.failure(AuthFailure("INVALID_RESPONSE")),
        )
        assertEquals(
            AuthMessage(R.string.auth_error_unexpected),
            AuthMessageMapper.failure(IOException("Private connection details")),
        )
    }

    @Test
    fun storageSaveAndClearFailuresKeepTheirSpecificMessages() {
        assertEquals(
            AuthMessage(R.string.auth_error_storage_save),
            AuthMessageMapper.failure(
                AuthFailure("STORAGE_ERROR", storageAction = StorageAction.SAVE),
            ),
        )
        assertEquals(
            AuthMessage(R.string.auth_error_storage_clear),
            AuthMessageMapper.failure(
                AuthFailure("STORAGE_ERROR", storageAction = StorageAction.CLEAR),
            ),
        )
    }
}
