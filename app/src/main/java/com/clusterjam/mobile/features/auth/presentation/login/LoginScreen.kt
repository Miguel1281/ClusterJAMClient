package com.clusterjam.mobile.features.auth.presentation.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.PrimaryButton
import com.clusterjam.mobile.core.ui.theme.ClusterJAMClientTheme
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import com.clusterjam.mobile.features.auth.presentation.components.*

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    state: LoginUiState = LoginUiState(),
    onEmailChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onEmailConfirmationClick: () -> Unit = {},
    notice: AuthMessage? = null,
) {
    AuthForm(
        title = stringResource(R.string.auth_login_title),
        subtitle = stringResource(R.string.auth_login_subtitle),
        feedbackKey = state.error ?: notice,
        action = {
            PrimaryButton(
                stringResource(
                    if (state.isLoading) R.string.auth_login_loading
                    else R.string.auth_login_submit,
                ),
                onClick = onLoginClick,
                enabled = state.canSubmit,
                loading = state.isLoading,
            )
        },
        feedback = {
            AuthFeedback(
                error = state.error?.takeIf { it.fieldKind() == null },
                notice = notice,
                success = true,
                action =
                    if (state.emailAwaitingConfirmation != null) {
                        {
                            TextButton(
                                onClick = onEmailConfirmationClick,
                                enabled = !state.isLoading,
                            ) {
                                Text(stringResource(R.string.auth_confirmation_title))
                            }
                        }
                    } else null,
            )
        },
    ) {
        AuthField(
            state.email,
            onEmailChange,
            stringResource(R.string.auth_email_label),
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Email,
            error =
                fieldMessage(state.error, AuthFieldKind.Email)
                    ?: state.emailError?.let { stringResource(AuthMessageMapper.validation(it)) },
        )
        AuthField(
            state.password,
            onPasswordChange,
            stringResource(R.string.auth_password_label),
            enabled = !state.isLoading,
            password = true,
            error = fieldMessage(state.error, AuthFieldKind.Password),
            onDone = { if (state.canSubmit) onLoginClick() },
        )
        TextButton(
            onClick = onForgotPasswordClick,
            enabled = !state.isLoading,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.auth_forgot_password))
        }
        TextButton(
            onClick = onNavigateToRegister,
            enabled = !state.isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.auth_register_link))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Compacto", showBackground = true, widthDp = 320, heightDp = 568)
@Preview(
    name = "Texto grande",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 1.6f,
)
@Composable
fun LoginScreenPreview() {
    ClusterJAMClientTheme { LoginScreen({}, {}, {}) }
}

@Preview(name = "Error de conexión", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginErrorPreview() {
    ClusterJAMClientTheme {
        LoginScreen(
            {},
            {},
            {},
            state =
                LoginUiState(
                    email = "cliente@ejemplo.com",
                    error = AuthMessage(R.string.auth_error_network),
                ),
        )
    }
}
