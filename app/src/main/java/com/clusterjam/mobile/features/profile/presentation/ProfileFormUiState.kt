package com.clusterjam.mobile.features.profile.presentation

import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.profile.domain.model.ProfileSnapshot
import com.clusterjam.mobile.features.profile.domain.validation.ProfileValidationError

enum class ProfileFormMode {
    ONBOARDING,
    EDIT,
}

data class ProfileFormUiState(
    val mode: ProfileFormMode = ProfileFormMode.ONBOARDING,
    val isOpen: Boolean = false,
    val fullName: String = "",
    val phone: String = "",
    val contactEmail: String = "",
    val snapshot: ProfileSnapshot? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val dirty: Boolean = false,
    val needsReload: Boolean = true,
    val needsReview: Boolean = false,
    val saved: Boolean = false,
    val error: AuthMessage? = null,
    val validationError: ProfileValidationError? = null,
    val retryAtMillis: Long = 0,
) {
    val isBusy
        get() = isLoading || isSaving

    val canSave
        get() = isOpen && !isBusy && snapshot != null && !needsReload && !needsReview && !saved
}
