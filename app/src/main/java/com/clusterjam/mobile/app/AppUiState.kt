package com.clusterjam.mobile.app

import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.presentation.AuthMessage

data class AppUiState(
    val session: SessionState = SessionState.Checking,
    val isBusy: Boolean = false,
    val notice: AuthMessage? = null,
)
