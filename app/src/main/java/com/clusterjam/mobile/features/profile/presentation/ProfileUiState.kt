package com.clusterjam.mobile.features.profile.presentation

import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.profile.domain.model.ProfileSnapshot

enum class ProfileLoadStatus {
    IDLE,
    LOADING,
    PROVISIONING,
    READY,
    ERROR,
}

data class ProfileUiState(
    val status: ProfileLoadStatus = ProfileLoadStatus.IDLE,
    val snapshot: ProfileSnapshot? = null,
    val error: AuthMessage? = null,
    val automaticRetry: Boolean = false,
) {
    val isBusy: Boolean
        get() = status == ProfileLoadStatus.LOADING || automaticRetry
}
