package com.clusterjam.mobile.features.auth.presentation.recovery

import com.clusterjam.mobile.features.auth.presentation.AuthMessage

enum class PasswordRecoveryStep {
    REQUEST_CODE,
    ENTER_CODE,
    NEW_CODE_REQUIRED,
    COMPLETED,
}

enum class PasswordRecoveryOperation {
    IDLE,
    REQUESTING,
    SUBMITTING,
    ERROR,
}

data class PasswordRecoveryUiState(
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirmation: String = "",
    val step: PasswordRecoveryStep = PasswordRecoveryStep.REQUEST_CODE,
    val operation: PasswordRecoveryOperation = PasswordRecoveryOperation.IDLE,
    val notice: AuthMessage? = null,
    val error: AuthMessage? = null,
    val cleanupFailure: AuthMessage? = null,
    val retryAtMillis: Long = 0,
) {
    val isBusy
        get() =
            operation == PasswordRecoveryOperation.REQUESTING ||
                operation == PasswordRecoveryOperation.SUBMITTING
}
