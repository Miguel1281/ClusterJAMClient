package com.clusterjam.mobile.features.auth.presentation.registration

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterClick: (String, String) -> Unit,
    isLoading: Boolean = false,
    error: AuthMessage? = null,
    email: String = "",
    onEmailChange: (String) -> Unit = {},
    retryAtMillis: Long = 0,
) {
    // Secrets intentionally remain in memory only, as in the original form.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val emailError = if (email.isBlank()) null else AuthValidator.emailError(email.trim())
    val passwordError = if (password.isEmpty()) null else AuthValidator.passwordError(password)
    val mismatch = confirmation.isNotEmpty() && confirmation != password
    val remaining = rememberRetrySeconds(retryAtMillis)
    val canSubmit =
        !isLoading &&
            remaining == 0L &&
            AuthValidator.emailError(email.trim()) == null &&
            AuthValidator.passwordError(password) == null &&
            password == confirmation
    val submit = { if (canSubmit) onRegisterClick(email.trim(), password) }

    AuthForm(
        title = stringResource(R.string.auth_register_title),
        subtitle = stringResource(R.string.auth_register_subtitle),
        feedbackKey = error,
        action = {
            PrimaryButton(
                stringResource(
                    if (isLoading) R.string.auth_register_loading
                    else R.string.auth_register_submit,
                ),
                onClick = submit,
                enabled = canSubmit,
                loading = isLoading,
            )
        },
        feedback = {
            AuthFeedback(
                error = error?.takeIf { it.fieldKind() == null },
                remainingSeconds = remaining,
            )
        },
    ) {
        AuthField(
            email,
            onEmailChange,
            stringResource(R.string.auth_email_label),
            enabled = !isLoading,
            keyboardType = KeyboardType.Email,
            error =
                fieldMessage(error, AuthFieldKind.Email)
                    ?: emailError?.let { stringResource(AuthMessageMapper.validation(it)) },
        )
        AuthField(
            password,
            { password = it },
            stringResource(R.string.auth_password_label),
            enabled = !isLoading,
            password = true,
            hint = stringResource(R.string.auth_password_hint),
            error =
                fieldMessage(error, AuthFieldKind.Password)
                    ?: passwordError?.let { stringResource(AuthMessageMapper.validation(it)) },
        )
        AuthField(
            confirmation,
            { confirmation = it },
            stringResource(R.string.auth_confirm_password_label),
            enabled = !isLoading,
            password = true,
            error = if (mismatch) stringResource(R.string.auth_password_mismatch) else null,
            onDone = submit,
        )
        TextButton(
            onClick = onNavigateToLogin,
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.auth_login_link))
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
    fontScale = 2f,
)
@Composable
fun RegisterScreenPreview() {
    ClusterJAMClientTheme { RegisterScreen({}, { _, _ -> }) }
}

@Preview(name = "Enviando", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RegisterLoadingPreview() {
    ClusterJAMClientTheme {
        RegisterScreen({}, { _, _ -> }, isLoading = true, email = "cliente@ejemplo.com")
    }
}
