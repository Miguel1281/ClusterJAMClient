package com.example.cluster_jam_client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cluster_jam_client.domain.auth.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthEvent {
    data class Notice(val message: String, val returnToLogin: Boolean = false) : AuthEvent()
}

data class AuthUiState(
    val isRestoring: Boolean = true,
    val startupError: String? = null,
    val isLoading: Boolean = false,
    val identity: AuthIdentity? = null,
    val recoveryEmail: String? = null
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()
    private val events = Channel<AuthEvent>(Channel.BUFFERED)
    val authEvent = events.receiveAsFlow()

    init { restoreSession() }

    fun restoreSession() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isRestoring = true, startupError = null, isLoading = true) }
        viewModelScope.launch {
            try {
                repository.restoreSession().fold(
                    onSuccess = { identity -> _uiState.update { it.copy(identity = identity) } },
                    onFailure = { error -> _uiState.update { it.copy(startupError = error.message ?: "No se pudo comprobar la sesión.") } }
                )
            } finally { _uiState.update { it.copy(isRestoring = false, isLoading = false) } }
        }
    }

    fun login(email: String, password: String) = perform {
        val session = repository.login(AuthCredentials(email.trim(), password)).getOrThrow()
        _uiState.update { it.copy(identity = session.identity) }
    }

    fun register(email: String, password: String) = perform {
        val registration = repository.register(AuthCredentials(email.trim(), password)).getOrThrow()
        val message = when (registration.status) {
            RegistrationStatus.PENDING_EMAIL_CONFIRMATION -> "Cuenta creada. Revisa tu correo y confírmalo antes de iniciar sesión."
            RegistrationStatus.ACTIVE -> "Cuenta creada. Ya puedes iniciar sesión."
        }
        events.send(AuthEvent.Notice(message, returnToLogin = true))
    }

    fun refreshIdentity() = perform {
        val identity = repository.currentIdentity().getOrThrow()
        _uiState.update { it.copy(identity = identity) }
    }

    fun renewSession() = perform {
        val session = repository.renewSession().getOrThrow()
        _uiState.update { it.copy(identity = session.identity) }
        events.send(AuthEvent.Notice("Sesión renovada."))
    }

    fun logout() = perform {
        val result = repository.logout()
        if ((result.exceptionOrNull() as? AuthFailure)?.code == "STORAGE_ERROR") result.getOrThrow()
        _uiState.update { it.copy(identity = null, recoveryEmail = null, startupError = null) }
        val message = if (result.isSuccess) "Sesión cerrada." else
            "Se cerró la sesión en este dispositivo, pero no se pudo confirmar el cierre en el servidor."
        events.send(AuthEvent.Notice(message, returnToLogin = true))
    }

    fun beginRecovery() { _uiState.update { it.copy(recoveryEmail = null) } }
    fun changeRecoveryEmail() {
        if (!_uiState.value.isLoading) _uiState.update { it.copy(recoveryEmail = null) }
    }

    fun requestPasswordRecovery(email: String) = perform {
        repository.requestPasswordRecovery(email.trim()).getOrThrow()
        _uiState.update { it.copy(recoveryEmail = email.trim()) }
        events.send(AuthEvent.Notice("Si la cuenta puede recibir correos de recuperación, recibirás un código. Revisa también el correo no deseado."))
    }

    fun recoverPassword(code: String, newPassword: String) = perform {
        val email = _uiState.value.recoveryEmail ?: throw AuthFailure("VALIDATION_ERROR", "Solicita primero tu código.")
        repository.recoverPassword(email, code, newPassword).getOrThrow()
        _uiState.update { it.copy(identity = null, recoveryEmail = null) }
        events.send(AuthEvent.Notice("Contraseña actualizada. Inicia sesión con tu nueva contraseña.", returnToLogin = true))
    }

    fun checkHealth() = perform {
        val health = repository.checkHealth().getOrThrow()
        events.send(AuthEvent.Notice(if (health.live && health.ready) "El servicio está disponible." else "El servicio está activo, pero todavía no está listo."))
    }

    private fun perform(action: suspend () -> Unit) {
        if (_uiState.value.isLoading || _uiState.value.isRestoring) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try { action() }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) {
                if ((error as? AuthFailure)?.code == "SESSION_ENDED") {
                    _uiState.update { it.copy(identity = null) }
                }
                events.send(AuthEvent.Notice(error.message ?: "No se pudo completar la operación."))
            } finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }
}
