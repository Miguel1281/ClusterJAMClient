package com.clusterjam.mobile.features.profile.presentation

import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure

object ProfileMessageMapper {
    fun failure(error: Throwable): AuthMessage {
        val failure = error as? ProfileFailure ?: return AuthMessage(R.string.auth_error_unexpected)
        return when (failure.code) {
            "PROFILE_NOT_READY" -> AuthMessage(R.string.profile_provisioning)
            "VALIDATION_ERROR",
            "PROFILE_CONTACT_EMAIL_REQUIRED" -> AuthMessage(R.string.profile_error_validation)
            "PRECONDITION_FAILED",
            "PRECONDITION_REQUIRED" -> AuthMessage(R.string.profile_error_revision)
            else -> AuthMessageMapper.failure(AuthFailure(failure.code, failure.status))
        }
    }
}
