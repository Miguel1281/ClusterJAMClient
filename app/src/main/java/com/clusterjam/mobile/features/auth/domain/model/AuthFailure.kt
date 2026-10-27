package com.clusterjam.mobile.features.auth.domain.model

import com.clusterjam.mobile.features.auth.domain.validation.AuthValidationError

enum class StorageAction {
    OPEN,
    READ,
    SAVE,
    CLEAR,
}

class AuthFailure(
    val code: String,
    val status: Int? = null,
    val validationErrors: List<String> = emptyList(),
    val traceId: String? = null,
    val validationError: AuthValidationError? = null,
    val storageAction: StorageAction? = null,
    val retryAfterMillis: Long? = null,
) : Exception(code)
