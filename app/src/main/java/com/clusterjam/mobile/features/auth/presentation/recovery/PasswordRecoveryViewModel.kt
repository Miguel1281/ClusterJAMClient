package com.clusterjam.mobile.features.auth.presentation.recovery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PasswordRecoveryViewModel(
    private val repository: AuthRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val restoredCodeStage =
        savedState.get<String>(STEP_KEY) == PasswordRecoveryStep.ENTER_CODE.name
    private val _uiState =
        MutableStateFlow(
            PasswordRecoveryUiState(
                email = savedState.get<String>(EMAIL_KEY).orEmpty(),
                step =
                    if (restoredCodeStage) PasswordRecoveryStep.ENTER_CODE
                    else PasswordRecoveryStep.REQUEST_CODE,
                notice =
                    if (restoredCodeStage) AuthMessage(R.string.auth_recovery_enter_again)
                    else null,
            ),
        )
    val uiState = _uiState.asStateFlow()

    fun begin(initialEmail: String = "") {
        if (_uiState.value.isBusy) return
        changeEmail(_uiState.value.email.ifBlank { initialEmail })
    }

    fun changeEmail(value: String) {
        if (_uiState.value.isBusy) return
        val email = value.trim()
        savedState[EMAIL_KEY] = email
        setStep(PasswordRecoveryStep.REQUEST_CODE)
        _uiState.update {
            it.copy(
                email = email,
                operation = PasswordRecoveryOperation.IDLE,
                error = null,
                notice = null,
                cleanupFailure = null,
            )
        }
    }

    fun useAnotherEmail() = changeEmail(_uiState.value.email)

    fun leave() {
        if (!_uiState.value.isBusy) useAnotherEmail()
    }

    fun dismissCompletion() = begin()

    // Called when the screen is disposed, including navigation or activity recreation.
    // Do not cancel a submitted operation: its 204 and local cleanup still need handling.
    fun clearSensitiveFields() {
        _uiState.update { it.copy(code = "", password = "", confirmation = "") }
    }

    fun changeCode(value: String) {
        if (!_uiState.value.isBusy)
            _uiState.update {
                it.copy(code = value.filter { it in '0'..'9' }.take(6), error = null)
            }
    }

    fun changePassword(value: String) {
        if (!_uiState.value.isBusy) _uiState.update { it.copy(password = value, error = null) }
    }

    fun changeConfirmation(value: String) {
        if (!_uiState.value.isBusy) _uiState.update { it.copy(confirmation = value, error = null) }
    }

    fun requestCode() {
        val form = _uiState.value
        if (form.isBusy || form.step == PasswordRecoveryStep.COMPLETED || isWaiting()) return
        if (!validateEmail()) return
        val nextOnFailure =
            if (form.step == PasswordRecoveryStep.REQUEST_CODE) form.step
            else PasswordRecoveryStep.NEW_CODE_REQUIRED
        setStep(nextOnFailure)
        run(PasswordRecoveryOperation.REQUESTING) {
            repository
                .requestPasswordRecovery(form.email)
                .fold(
                    onSuccess = {
                        setStep(PasswordRecoveryStep.ENTER_CODE)
                        _uiState.update {
                            it.copy(notice = AuthMessage(R.string.auth_recovery_requested))
                        }
                    },
                    onFailure = ::showFailure,
                )
        }
    }

    fun recoverPassword() {
        val form = _uiState.value
        if (form.isBusy || form.step != PasswordRecoveryStep.ENTER_CODE || isWaiting()) return
        if (!validateEmail()) return
        val validation =
            AuthValidator.codeError(form.code) ?: AuthValidator.passwordError(form.password)
        if (validation != null) {
            _uiState.update {
                it.copy(error = AuthMessage(AuthMessageMapper.validation(validation)))
            }
            return
        }
        if (form.password != form.confirmation) {
            _uiState.update { it.copy(error = AuthMessage(R.string.auth_password_mismatch)) }
            return
        }
        run(PasswordRecoveryOperation.SUBMITTING) {
            val result = repository.recoverPassword(form.email, form.code, form.password)
            // A rejected or interrupted update may also have consumed the code.
            setStep(
                if (result.isSuccess) PasswordRecoveryStep.COMPLETED
                else PasswordRecoveryStep.NEW_CODE_REQUIRED,
            )
            result.fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            notice = AuthMessage(R.string.auth_password_updated),
                            cleanupFailure =
                                updated.cleanupFailure?.let(AuthMessageMapper::failure),
                        )
                    }
                },
                onFailure = { error ->
                    showFailure(error)
                    _uiState.update {
                        it.copy(notice = AuthMessage(R.string.auth_recovery_fresh_code))
                    }
                },
            )
        }
    }

    private fun validateEmail(): Boolean {
        val error = AuthValidator.emailError(_uiState.value.email) ?: return true
        _uiState.update { it.copy(error = AuthMessage(AuthMessageMapper.validation(error))) }
        return false
    }

    private fun isWaiting() = nowMillis() < _uiState.value.retryAtMillis

    private fun setStep(step: PasswordRecoveryStep) {
        savedState[STEP_KEY] =
            if (step == PasswordRecoveryStep.ENTER_CODE) step.name
            else PasswordRecoveryStep.REQUEST_CODE.name
        _uiState.update { it.copy(step = step, code = "", password = "", confirmation = "") }
    }

    private fun showFailure(error: Throwable) {
        val retryAfter = (error as? AuthFailure)?.retryAfterMillis ?: 0
        val now = nowMillis()
        val deadline = now + retryAfter.coerceIn(0, Long.MAX_VALUE - now)
        _uiState.update {
            it.copy(
                operation = PasswordRecoveryOperation.ERROR,
                error = AuthMessageMapper.failure(error),
                notice = null,
                retryAtMillis = maxOf(it.retryAtMillis, deadline),
            )
        }
    }

    private fun run(operation: PasswordRecoveryOperation, action: suspend () -> Unit) {
        _uiState.update { it.copy(operation = operation, error = null, notice = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (operation == PasswordRecoveryOperation.SUBMITTING) {
                    setStep(PasswordRecoveryStep.NEW_CODE_REQUIRED)
                    showFailure(error)
                    _uiState.update {
                        it.copy(notice = AuthMessage(R.string.auth_recovery_fresh_code))
                    }
                } else showFailure(error)
            } finally {
                _uiState.update {
                    if (it.operation == operation)
                        it.copy(operation = PasswordRecoveryOperation.IDLE)
                    else it
                }
            }
        }
    }

    companion object {
        const val EMAIL_KEY = "recovery.email"
        const val STEP_KEY = "recovery.step"
    }
}
