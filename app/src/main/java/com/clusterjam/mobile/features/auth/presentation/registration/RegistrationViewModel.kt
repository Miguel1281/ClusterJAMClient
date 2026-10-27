package com.clusterjam.mobile.features.auth.presentation.registration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.AuthCredentials
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.RegistrationStatus
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegistrationViewModel(
    private val repository: AuthRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val restoredConfirmation =
        savedState.get<String>(STEP_KEY) == RegistrationStep.WAITING_CODE.name
    private val _uiState =
        MutableStateFlow(
            RegistrationUiState(
                email = savedState.get<String>(EMAIL_KEY).orEmpty(),
                step =
                    if (restoredConfirmation) RegistrationStep.WAITING_CODE
                    else RegistrationStep.FORM,
                notice =
                    if (restoredConfirmation) AuthMessage(R.string.auth_confirmation_enter_again)
                    else null,
            ),
        )
    val uiState = _uiState.asStateFlow()

    fun changeEmail(email: String) {
        if (_uiState.value.isBusy) return
        savedState[EMAIL_KEY] = email.trim()
        _uiState.update {
            it.copy(
                email = email,
                code = "",
                error = null,
                notice = null,
                operation = RegistrationOperation.IDLE,
            )
        }
    }

    fun changeCode(value: String) {
        if (!_uiState.value.isBusy)
            _uiState.update {
                it.copy(
                    code = value.filter { digit -> digit in '0'..'9' }.take(6),
                    error = null,
                    operation = RegistrationOperation.IDLE,
                )
            }
    }

    fun startRegistration(email: String = _uiState.value.email) {
        if (_uiState.value.isBusy) return
        changeEmail(email)
        setStep(RegistrationStep.FORM)
    }

    fun beginConfirmation(email: String) {
        if (_uiState.value.isBusy) return
        changeEmail(email.trim())
        setStep(RegistrationStep.WAITING_CODE)
        _uiState.update { it.copy(notice = AuthMessage(R.string.auth_confirmation_enter_code)) }
    }

    fun cancel() = startRegistration()

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun register(email: String, password: String) {
        if (_uiState.value.isBusy || _uiState.value.step != RegistrationStep.FORM || isWaiting())
            return
        changeEmail(email.trim())
        run(RegistrationOperation.REGISTERING) {
            repository
                .register(AuthCredentials(email.trim(), password))
                .fold(
                    onSuccess = { registration ->
                        changeEmailAfterRequest(registration.identity.email)
                        when (registration.status) {
                            RegistrationStatus.PENDING_EMAIL_CONFIRMATION -> {
                                setStep(RegistrationStep.WAITING_CODE)
                                _uiState.update {
                                    it.copy(
                                        notice = AuthMessage(R.string.auth_confirmation_enter_code),
                                    )
                                }
                            }
                            RegistrationStatus.ACTIVE -> {
                                setStep(RegistrationStep.COMPLETED)
                                _uiState.update {
                                    it.copy(notice = AuthMessage(R.string.auth_registered_active))
                                }
                            }
                        }
                    },
                    onFailure = ::showFailure,
                )
        }
    }

    fun confirmEmail() {
        val form = _uiState.value
        if (form.isBusy || form.step != RegistrationStep.WAITING_CODE || isWaiting()) return
        if (!validateEmail()) return
        AuthValidator.codeError(form.code)?.let {
            showFailure(AuthFailure("VALIDATION_ERROR", validationError = it))
            return
        }
        run(RegistrationOperation.CONFIRMING) {
            val result = repository.confirmEmail(form.email.trim(), form.code)
            _uiState.update { it.copy(code = "") }
            result.fold(
                onSuccess = {
                    setStep(RegistrationStep.COMPLETED)
                    _uiState.update {
                        it.copy(notice = AuthMessage(R.string.auth_confirmation_processed))
                    }
                },
                onFailure = ::showFailure,
            )
        }
    }

    fun resendCode() {
        val form = _uiState.value
        if (
            form.isBusy ||
                form.step != RegistrationStep.WAITING_CODE ||
                isWaiting() ||
                !validateEmail()
        )
            return
        run(RegistrationOperation.RESENDING) {
            repository
                .requestEmailConfirmation(form.email.trim())
                .fold(
                    onSuccess = { response ->
                        applyRetryAfter(response.retryAfterMillis)
                        _uiState.update {
                            it.copy(
                                code = "",
                                notice = AuthMessage(R.string.auth_confirmation_requested),
                            )
                        }
                    },
                    onFailure = ::showFailure,
                )
        }
    }

    private fun validateEmail(): Boolean {
        val error = AuthValidator.emailError(_uiState.value.email.trim()) ?: return true
        showFailure(AuthFailure("VALIDATION_ERROR", validationError = error))
        return false
    }

    private fun isWaiting() = nowMillis() < _uiState.value.retryAtMillis

    private fun applyRetryAfter(millis: Long?) {
        if (millis != null && millis > 0) {
            val now = nowMillis()
            val deadline = now + millis.coerceAtMost(Long.MAX_VALUE - now)
            _uiState.update { it.copy(retryAtMillis = maxOf(it.retryAtMillis, deadline)) }
        }
    }

    private fun showFailure(error: Throwable) {
        applyRetryAfter((error as? AuthFailure)?.retryAfterMillis)
        _uiState.update {
            it.copy(
                operation = RegistrationOperation.ERROR,
                error = AuthMessageMapper.failure(error),
                notice = null,
            )
        }
    }

    private fun changeEmailAfterRequest(email: String) {
        savedState[EMAIL_KEY] = email.trim()
        _uiState.update { it.copy(email = email.trim(), code = "") }
    }

    private fun setStep(step: RegistrationStep) {
        // Only email and a resumable stage are saved. Never password, OTP or an in-flight action.
        savedState[STEP_KEY] =
            if (step == RegistrationStep.WAITING_CODE) step.name else RegistrationStep.FORM.name
        _uiState.update { it.copy(step = step, code = "") }
    }

    private fun run(operation: RegistrationOperation, action: suspend () -> Unit) {
        _uiState.update { it.copy(operation = operation, error = null, notice = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showFailure(error)
            } finally {
                _uiState.update {
                    if (it.operation == operation) it.copy(operation = RegistrationOperation.IDLE)
                    else it
                }
            }
        }
    }

    companion object {
        const val EMAIL_KEY = "registration.email"
        const val STEP_KEY = "registration.step"
    }
}
