package com.clusterjam.mobile.features.profile.data.mapper

import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure
import java.io.IOException

// SessionAccess already parses HTTP Problem Details and coordinates Auth's session state.
internal fun mapProfileError(error: Exception): ProfileFailure =
    when (error) {
        is ProfileFailure -> error
        is AuthFailure ->
            ProfileFailure(
                code = error.code,
                status = error.status,
                validationErrors = error.validationErrors,
                traceId = error.traceId,
                retryAfterMillis = error.retryAfterMillis,
            )
        is IOException -> ProfileFailure("NETWORK_ERROR")
        else -> ProfileFailure("UNEXPECTED_ERROR")
    }
