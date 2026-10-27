package com.clusterjam.mobile.features.auth.data.mapper

import com.clusterjam.mobile.core.network.ProblemDetailsParser
import com.clusterjam.mobile.core.network.parseRetryAfterMillis
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import java.io.IOException
import retrofit2.HttpException

private val problemDetailsParser = ProblemDetailsParser()

internal fun mapAuthError(
    error: Exception,
    nowMillis: Long = System.currentTimeMillis(),
): AuthFailure {
    if (error is AuthFailure) return error
    if (error is IOException) return AuthFailure("NETWORK_ERROR")
    if (error !is HttpException) return AuthFailure("UNEXPECTED_ERROR")

    val status = error.code()
    val problem = problemDetailsParser.parse(error.response()?.errorBody())
    return AuthFailure(
        code = problem?.code?.takeIf { it.isNotBlank() } ?: "HTTP_$status",
        status = status,
        validationErrors = problem?.errors.orEmpty().filterNotNull().filter { it.isNotBlank() },
        traceId = problem?.traceId?.takeIf { it.isNotBlank() },
        retryAfterMillis =
            parseRetryAfterMillis(error.response()?.headers()?.get("Retry-After"), nowMillis),
    )
}
