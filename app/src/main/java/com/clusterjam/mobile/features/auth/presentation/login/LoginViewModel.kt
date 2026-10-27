package com.clusterjam.mobile.features.auth.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.features.auth.domain.model.AuthCredentials
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun changeEmail(value: String) {
        if (!_uiState.value.isLoading) _uiState.update { it.copy(email = value, error = null) }
    }

    fun changePassword(value: String) {
        if (!_uiState.value.isLoading) _uiState.update { it.copy(password = value, error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun login() {
        val form = _uiState.value
        if (!form.canSubmit) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.login(AuthCredentials(form.email.trim(), form.password))
                _uiState.update {
                    it.copy(
                        password = "",
                        error =
                            result
                                .exceptionOrNull()
                                ?.takeIf {
                                    repository.sessionState.value == SessionState.Unauthenticated
                                }
                                ?.let(AuthMessageMapper::failure),
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
