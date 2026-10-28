package com.clusterjam.mobile.features.auth.presentation.recovery

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.PrimaryButton
import com.clusterjam.mobile.core.ui.theme.ClusterJAMClientTheme
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import com.clusterjam.mobile.features.auth.presentation.components.*

@Composable
fun PasswordRecoveryScreen(
    state: PasswordRecoveryUiState,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    onRecoverPassword: () -> Unit,
    onChangeEmail: () -> Unit,
    onBack: () -> Unit,
) {
    val enteringCode = state.step == PasswordRecoveryStep.ENTER_CODE
    val remaining = rememberRetrySeconds(state.retryAtMillis)
    val canSend = !state.isBusy && remaining == 0L && AuthValidator.emailError(state.email) == null
    val canRecover =
        canSend &&
            AuthValidator.codeError(state.code) == null &&
            AuthValidator.passwordError(state.password) == null &&
            state.password == state.confirmation
    val emailError = if (state.email.isBlank()) null else AuthValidator.emailError(state.email)
    val codeError = if (state.code.isEmpty()) null else AuthValidator.codeError(state.code)
    val passwordError =
        if (state.password.isEmpty()) null else AuthValidator.passwordError(state.password)
    val mismatch = state.confirmation.isNotEmpty() && state.password != state.confirmation
    val buttonLabel =
        when {
            state.operation == PasswordRecoveryOperation.SUBMITTING ->
                R.string.auth_recovery_updating
            state.operation == PasswordRecoveryOperation.REQUESTING ->
                R.string.auth_recovery_requesting
            enteringCode -> R.string.auth_recovery_update
            state.step == PasswordRecoveryStep.NEW_CODE_REQUIRED -> R.string.auth_recovery_resend
            else -> R.string.auth_recovery_request
        }
    AuthForm(
        feedbackKey = state.error ?: state.notice,
        title =
            stringResource(
                if (enteringCode) R.string.auth_recovery_password else R.string.auth_recovery_title,
            ),
        subtitle =
            if (enteringCode) stringResource(R.string.auth_recovery_pending_email, state.email)
            else stringResource(R.string.auth_recovery_instructions),
        action = {
            if (state.step != PasswordRecoveryStep.COMPLETED) {
                PrimaryButton(
                    stringResource(buttonLabel),
                    if (enteringCode) onRecoverPassword else onRequestCode,
                    enabled = if (enteringCode) canRecover else canSend,
                    loading = state.isBusy,
                )
            }
        },
        feedback = {
            // After an uncertain update the old fields are removed; keep the error visible here.
            val visibleFields =
                if (enteringCode) setOf(AuthFieldKind.Code, AuthFieldKind.Password)
                else setOf(AuthFieldKind.Email)
            val globalError = state.error?.takeIf { it.fieldKind() !in visibleFields }
            AuthFeedback(error = globalError, notice = state.notice, remainingSeconds = remaining)
            if (state.step == PasswordRecoveryStep.NEW_CODE_REQUIRED && globalError != null) {
                Text(
                    stringResource(R.string.auth_recovery_fresh_code),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    ) {
        if (enteringCode) {
            AuthField(
                state.code,
                onCodeChange,
                stringResource(R.string.auth_recovery_code),
                enabled = !state.isBusy,
                keyboardType = KeyboardType.NumberPassword,
                error =
                    fieldMessage(state.error, AuthFieldKind.Code)
                        ?: codeError?.let { stringResource(AuthMessageMapper.validation(it)) },
            )
            AuthField(
                state.password,
                onPasswordChange,
                stringResource(R.string.auth_recovery_password),
                enabled = !state.isBusy,
                password = true,
                hint = stringResource(R.string.auth_password_hint),
                error =
                    fieldMessage(state.error, AuthFieldKind.Password)
                        ?: passwordError?.let { stringResource(AuthMessageMapper.validation(it)) },
            )
            AuthField(
                state.confirmation,
                onConfirmationChange,
                stringResource(R.string.auth_recovery_confirmation),
                enabled = !state.isBusy,
                password = true,
                error = if (mismatch) stringResource(R.string.auth_password_mismatch) else null,
                onDone = { if (canRecover) onRecoverPassword() },
            )
            TextButton(onClick = onRequestCode, enabled = canSend) {
                Text(stringResource(R.string.auth_recovery_resend))
            }
            TextButton(onClick = onChangeEmail, enabled = !state.isBusy) {
                Text(stringResource(R.string.auth_recovery_change_email))
            }
        } else if (state.step != PasswordRecoveryStep.COMPLETED) {
            AuthField(
                state.email,
                onEmailChange,
                stringResource(R.string.auth_recovery_email),
                enabled = !state.isBusy,
                keyboardType = KeyboardType.Email,
                error =
                    fieldMessage(state.error, AuthFieldKind.Email)
                        ?: emailError?.let { stringResource(AuthMessageMapper.validation(it)) },
                onDone = { if (canSend) onRequestCode() },
            )
        }
        TextButton(onClick = onBack, enabled = !state.isBusy) {
            Text(stringResource(R.string.auth_recovery_back))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RecoveryRequestPreview() {
    RecoveryPreviewContent(PasswordRecoveryUiState())
}

@Preview(name = "Restablecimiento", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(
    name = "Compacto y texto grande",
    showBackground = true,
    widthDp = 320,
    heightDp = 568,
    fontScale = 1.6f,
)
@Composable
private fun RecoveryCodePreview() {
    RecoveryPreviewContent(
        PasswordRecoveryUiState(
            email = "cliente@ejemplo.com",
            step = PasswordRecoveryStep.ENTER_CODE,
        ),
    )
}

@Preview(name = "Resultado incierto", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RecoveryErrorPreview() {
    RecoveryPreviewContent(
        PasswordRecoveryUiState(
            email = "cliente@ejemplo.com",
            step = PasswordRecoveryStep.NEW_CODE_REQUIRED,
            error = AuthMessage(R.string.auth_error_network),
            notice = AuthMessage(R.string.auth_recovery_fresh_code),
        ),
    )
}

@Composable
private fun RecoveryPreviewContent(state: PasswordRecoveryUiState) {
    ClusterJAMClientTheme { PasswordRecoveryScreen(state, {}, {}, {}, {}, {}, {}, {}, {}) }
}
