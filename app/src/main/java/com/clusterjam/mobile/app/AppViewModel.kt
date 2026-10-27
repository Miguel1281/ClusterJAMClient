package com.clusterjam.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState(repository.sessionState.value))
    val uiState = _uiState.asStateFlow()
    private var sessionJob: Job? = null
    private var hasStarted = false

    init {
        viewModelScope.launch {
            repository.sessionState.collect { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
        retryIdentity()
    }

    fun retryIdentity() = perform { repository.restoreSession() }

    fun refreshIdentity() {
        repository.invalidateIdentity()
        retryIdentity()
    }

    fun onBackground() {
        repository.invalidateIdentity()
    }

    fun onForeground() {
        if (!hasStarted) {
            hasStarted = true
            return
        }
        val session = repository.sessionState.value
        if (session is SessionState.Authenticated || session == SessionState.Checking) {
            refreshIdentity()
        }
    }

    fun logout() = perform {
        val result = repository.logout()
        val message =
            when {
                result.isSuccess -> AuthMessage(R.string.auth_logged_out)
                repository.sessionState.value == SessionState.Unauthenticated ->
                    AuthMessage(R.string.auth_logged_out_locally)
                else -> AuthMessageMapper.failure(requireNotNull(result.exceptionOrNull()))
            }
        _uiState.update { it.copy(notice = message) }
        result
    }

    fun discardSession() = perform { repository.discardSession() }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun perform(action: suspend () -> Result<*>) {
        if (sessionJob?.isActive == true) return
        _uiState.update { it.copy(isBusy = true, notice = null) }
        sessionJob = viewModelScope.launch {
            try {
                action()
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }
}
