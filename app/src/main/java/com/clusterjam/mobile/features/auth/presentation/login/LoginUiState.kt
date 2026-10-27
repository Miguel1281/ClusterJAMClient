package com.clusterjam.mobile.features.auth.presentation.login

import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import com.clusterjam.mobile.features.auth.presentation.AuthMessage

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthMessage? = null,
) {
    val emailError
        get() = if (email.isBlank()) null else AuthValidator.emailError(email.trim())

    val canSubmit
        get() =
            !isLoading && AuthValidator.emailError(email.trim()) == null && password.isNotEmpty()
}
