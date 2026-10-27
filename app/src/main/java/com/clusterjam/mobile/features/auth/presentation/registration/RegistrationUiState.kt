package com.clusterjam.mobile.features.auth.presentation.registration

import com.clusterjam.mobile.features.auth.presentation.AuthMessage

enum class RegistrationStep {
    FORM,
    WAITING_CODE,
    COMPLETED,
}

enum class RegistrationOperation {
    IDLE,
    REGISTERING,
    RESENDING,
    CONFIRMING,
    ERROR,
}

data class RegistrationUiState(
    val email: String = "",
    val code: String = "",
    val step: RegistrationStep = RegistrationStep.FORM,
    val operation: RegistrationOperation = RegistrationOperation.IDLE,
    val error: AuthMessage? = null,
    val notice: AuthMessage? = null,
    val retryAtMillis: Long = 0,
) {
    val isBusy
        get() =
            operation in
                setOf(
                    RegistrationOperation.REGISTERING,
                    RegistrationOperation.RESENDING,
                    RegistrationOperation.CONFIRMING,
                )
}
