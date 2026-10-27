package com.clusterjam.mobile.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthEvent {
    data class Notice(val message: AuthMessage, val returnToLogin: Boolean = false) : AuthEvent()
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val recoveryEmail: String? = null,
    val error: AuthMessage? = null,
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()
    private val events = Channel<AuthEvent>(Channel.BUFFERED)
    val authEvent = events.receiveAsFlow()

    fun beginRecovery() {
        _uiState.update { it.copy(recoveryEmail = null, error = null) }
    }

    fun changeRecoveryEmail() {
        if (!_uiState.value.isLoading)
            _uiState.update { it.copy(recoveryEmail = null, error = null) }
    }

    fun requestPasswordRecovery(email: String) = perform {
        repository.requestPasswordRecovery(email.trim()).getOrThrow()
        _uiState.update { it.copy(recoveryEmail = email.trim()) }
        events.send(AuthEvent.Notice(AuthMessage(R.string.auth_recovery_requested)))
    }

    fun recoverPassword(code: String, newPassword: String) = perform {
        val email =
            _uiState.value.recoveryEmail
                ?: throw AuthFailure(
                    "VALIDATION_ERROR",
                    validationError = AuthValidationError.RECOVERY_CODE_REQUIRED,
                )
        repository.recoverPassword(email, code, newPassword).getOrThrow()
        _uiState.update { it.copy(recoveryEmail = null, error = null) }
        events.send(
            AuthEvent.Notice(AuthMessage(R.string.auth_password_updated), returnToLogin = true),
        )
    }

    private fun perform(action: suspend () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(error = AuthMessageMapper.failure(error)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
