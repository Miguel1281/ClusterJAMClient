package com.clusterjam.mobile.features.profile.domain.model

import com.clusterjam.mobile.features.profile.domain.validation.ProfileValidationError

class ProfileFailure(
    val code: String,
    val status: Int? = null,
    val validationErrors: List<String> = emptyList(),
    val traceId: String? = null,
    val retryAfterMillis: Long? = null,
    val validationError: ProfileValidationError? = null,
) : Exception(code)
