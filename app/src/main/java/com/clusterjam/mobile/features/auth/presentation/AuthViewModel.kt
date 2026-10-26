package com.clusterjam.mobile.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.AuthCredentials
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.AuthIdentity
import com.clusterjam.mobile.features.auth.domain.model.RegistrationStatus
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
    val isRestoring: Boolean = true,
    val startupError: AuthMessage? = null,
    val isLoading: Boolean = false,
    val identity: AuthIdentity? = null,
    val recoveryEmail: String? = null
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()
    private val events = Channel<AuthEvent>(Channel.BUFFERED)
    val authEvent = events.receiveAsFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isRestoring = true, startupError = null, isLoading = true) }
        viewModelScope.launch {
            try {
                repository.restoreSession().fold(
                    onSuccess = { identity -> _uiState.update { it.copy(identity = identity) } },
                    onFailure = { error ->
                        _uiState.update { it.copy(startupError = AuthMessageMapper.failure(error)) }
                    }
                )
            } finally {
                _uiState.update { it.copy(isRestoring = false, isLoading = false) }
            }
        }
    }

    fun login(email: String, password: String) = perform {
        val session = repository.login(AuthCredentials(email.trim(), password)).getOrThrow()
        _uiState.update { it.copy(identity = session.identity) }
    }

    fun register(email: String, password: String) = perform {
        val registration = repository.register(AuthCredentials(email.trim(), password)).getOrThrow()
        val message = when (registration.status) {
            RegistrationStatus.PENDING_EMAIL_CONFIRMATION -> R.string.auth_registered_pending
            RegistrationStatus.ACTIVE -> R.string.auth_registered_active
        }
        events.send(AuthEvent.Notice(AuthMessage(message), returnToLogin = true))
    }

    fun refreshIdentity() = perform {
        val identity = repository.currentIdentity().getOrThrow()
        _uiState.update { it.copy(identity = identity) }
    }

    fun renewSession() = perform {
        val session = repository.renewSession().getOrThrow()
        _uiState.update { it.copy(identity = session.identity) }
        events.send(AuthEvent.Notice(AuthMessage(R.string.auth_session_renewed)))
    }

    fun logout() = perform {
        val result = repository.logout()
        if ((result.exceptionOrNull() as? AuthFailure)?.code == "STORAGE_ERROR") result.getOrThrow()
        _uiState.update { it.copy(identity = null, recoveryEmail = null, startupError = null) }
        val message = if (result.isSuccess) {
            R.string.auth_logged_out
        } else {
            R.string.auth_logged_out_locally
        }
        events.send(AuthEvent.Notice(AuthMessage(message), returnToLogin = true))
    }

    fun beginRecovery() {
        _uiState.update { it.copy(recoveryEmail = null) }
    }

    fun changeRecoveryEmail() {
        if (!_uiState.value.isLoading) _uiState.update { it.copy(recoveryEmail = null) }
    }

    fun requestPasswordRecovery(email: String) = perform {
        repository.requestPasswordRecovery(email.trim()).getOrThrow()
        _uiState.update { it.copy(recoveryEmail = email.trim()) }
        events.send(AuthEvent.Notice(AuthMessage(R.string.auth_recovery_requested)))
    }

    fun recoverPassword(code: String, newPassword: String) = perform {
        val email = _uiState.value.recoveryEmail ?: throw AuthFailure(
            "VALIDATION_ERROR", validationError = AuthValidationError.RECOVERY_CODE_REQUIRED
        )
        repository.recoverPassword(email, code, newPassword).getOrThrow()
        _uiState.update { it.copy(identity = null, recoveryEmail = null) }
        events.send(
            AuthEvent.Notice(AuthMessage(R.string.auth_password_updated), returnToLogin = true)
        )
    }

    private fun perform(action: suspend () -> Unit) {
        if (_uiState.value.isLoading || _uiState.value.isRestoring) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if ((error as? AuthFailure)?.code == "SESSION_ENDED") {
                    _uiState.update { it.copy(identity = null) }
                }
                events.send(AuthEvent.Notice(AuthMessageMapper.failure(error)))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
