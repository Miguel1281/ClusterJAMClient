package com.clusterjam.mobile.features.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure
import com.clusterjam.mobile.features.profile.domain.model.ProfileSnapshot
import com.clusterjam.mobile.features.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()
    private var visible = false
    private var ownerId: String? = null
    private var operationId = 0L
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                val nextOwner = (session as? SessionState.Authenticated)?.identity?.userId
                if (nextOwner != ownerId) {
                    stopLoading()
                    ownerId = nextOwner
                    _uiState.value = ProfileUiState()
                    if (visible && nextOwner != null) refresh()
                }
            }
        }
    }

    fun onVisible() {
        if (visible) return
        visible = true
        refresh()
    }

    fun onHidden() {
        visible = false
        stopLoading()
        _uiState.value = ProfileUiState()
    }

    fun refresh() {
        val identity = (authRepository.sessionState.value as? SessionState.Authenticated)?.identity
        if (!visible || identity == null || _uiState.value.isBusy) return
        ownerId = identity.userId
        val owner = identity.userId
        val operation = ++operationId
        _uiState.value = ProfileUiState(status = ProfileLoadStatus.LOADING)
        loadJob = viewModelScope.launch {
            try {
                // One initial read, then at most three provisioning-only retries.
                for ((attempt, waitMillis) in listOf(0L, 1000L, 2000L, 4000L).withIndex()) {
                    if (waitMillis > 0) {
                        _uiState.value =
                            ProfileUiState(
                                status = ProfileLoadStatus.PROVISIONING,
                                automaticRetry = true,
                            )
                        delay(waitMillis)
                    }
                    if (!isCurrent(operation, owner)) return@launch
                    val result = repository.getMyProfile()
                    if (!isCurrent(operation, owner)) return@launch
                    val snapshot = result.getOrNull()
                    if (snapshot != null) {
                        _uiState.value =
                            if (snapshot.profile.userId.equals(owner, ignoreCase = true)) {
                                ProfileUiState(
                                    status = ProfileLoadStatus.READY,
                                    snapshot = snapshot,
                                )
                            } else {
                                ProfileUiState(
                                    status = ProfileLoadStatus.ERROR,
                                    error = AuthMessage(R.string.auth_error_identity_mismatch),
                                )
                            }
                        return@launch
                    }
                    val failure = result.exceptionOrNull() ?: ProfileFailure("UNEXPECTED_ERROR")
                    if (
                        failure is ProfileFailure &&
                            failure.code == "PROFILE_NOT_READY" &&
                            failure.status == 404
                    ) {
                        if (attempt < 3) continue
                        _uiState.value = ProfileUiState(status = ProfileLoadStatus.PROVISIONING)
                    } else {
                        _uiState.value =
                            ProfileUiState(
                                status = ProfileLoadStatus.ERROR,
                                error = ProfileMessageMapper.failure(failure),
                            )
                    }
                    return@launch
                }
            } finally {
                if (operation == operationId && _uiState.value.isBusy)
                    _uiState.value = ProfileUiState()
            }
        }
    }

    fun acceptServerProfile(snapshot: ProfileSnapshot) {
        val owner =
            (authRepository.sessionState.value as? SessionState.Authenticated)?.identity?.userId
                ?: return
        if (!visible || !snapshot.profile.userId.equals(owner, true)) return
        stopLoading()
        ownerId = owner
        _uiState.value = ProfileUiState(status = ProfileLoadStatus.READY, snapshot = snapshot)
    }

    private fun isCurrent(operation: Long, owner: String): Boolean =
        visible &&
            operation == operationId &&
            ownerId == owner &&
            (authRepository.sessionState.value as? SessionState.Authenticated)?.identity?.userId ==
                owner

    private fun stopLoading() {
        operationId++
        loadJob?.cancel()
        loadJob = null
    }
}
