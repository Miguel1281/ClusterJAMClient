package com.clusterjam.mobile.features.auth.presentation.registration

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.PrimaryButton
import com.clusterjam.mobile.core.ui.theme.ClusterJAMClientTheme
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import com.clusterjam.mobile.features.auth.presentation.components.*

@Composable
fun EmailConfirmationScreen(
    state: RegistrationUiState,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val remaining = rememberRetrySeconds(state.retryAtMillis)
    val canSend =
        !state.isBusy && remaining == 0L && AuthValidator.emailError(state.email.trim()) == null
    val canConfirm = canSend && AuthValidator.codeError(state.code) == null
    val emailError =
        if (state.email.isBlank()) null else AuthValidator.emailError(state.email.trim())
    val codeError = if (state.code.isEmpty()) null else AuthValidator.codeError(state.code)
    AuthForm(
        title = stringResource(R.string.auth_confirmation_title),
        subtitle = stringResource(R.string.auth_confirmation_instructions),
        feedbackKey = state.error ?: state.notice,
        action = {
            PrimaryButton(
                stringResource(
                    if (state.operation == RegistrationOperation.CONFIRMING)
                        R.string.auth_confirmation_confirming
                    else R.string.auth_confirmation_submit,
                ),
                onClick = onConfirm,
                enabled = canConfirm,
                loading = state.operation == RegistrationOperation.CONFIRMING,
            )
        },
        feedback = {
            AuthFeedback(
                error = state.error?.takeIf { it.fieldKind() == null },
                notice = state.notice,
                remainingSeconds = remaining,
            )
        },
    ) {
        AuthField(
            state.email,
            onEmailChange,
            stringResource(R.string.auth_confirmation_email),
            enabled = !state.isBusy,
            keyboardType = KeyboardType.Email,
            hint = stringResource(R.string.auth_confirmation_change_email),
            error =
                fieldMessage(state.error, AuthFieldKind.Email)
                    ?: emailError?.let { stringResource(AuthMessageMapper.validation(it)) },
        )
        AuthField(
            state.code,
            onCodeChange,
            stringResource(R.string.auth_confirmation_code),
            enabled = !state.isBusy,
            keyboardType = KeyboardType.NumberPassword,
            error =
                fieldMessage(state.error, AuthFieldKind.Code)
                    ?: codeError?.let { stringResource(AuthMessageMapper.validation(it)) },
            onDone = { if (canConfirm) onConfirm() },
        )
        TextButton(onClick = onResend, enabled = canSend) {
            if (state.operation == RegistrationOperation.RESENDING) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            Text(stringResource(R.string.auth_confirmation_resend))
        }
        TextButton(onClick = onBackToLogin, enabled = !state.isBusy) {
            Text(stringResource(R.string.auth_confirmation_back_login))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(
    name = "Texto grande",
    showBackground = true,
    widthDp = 320,
    heightDp = 568,
    fontScale = 1.6f,
)
@Composable
private fun ConfirmationPreview() {
    ClusterJAMClientTheme {
        EmailConfirmationScreen(
            RegistrationUiState(
                email = "cliente@ejemplo.com",
                step = RegistrationStep.WAITING_CODE,
            ),
            {},
            {},
            {},
            {},
            {},
        )
    }
}
