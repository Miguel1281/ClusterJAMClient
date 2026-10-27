package com.clusterjam.mobile.features.auth.presentation.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import kotlinx.coroutines.delay

@Composable
fun EmailConfirmationScreen(
    state: RegistrationUiState,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // This effect only updates a display clock; it never submits or resends an OTP.
    LaunchedEffect(state.retryAtMillis) {
        now = System.currentTimeMillis()
        while (now < state.retryAtMillis) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = (state.retryAtMillis - now).coerceAtLeast(0)
    val seconds = remaining / 1000 + if (remaining % 1000 > 0) 1 else 0
    val canSend =
        !state.isBusy && remaining == 0L && AuthValidator.emailError(state.email.trim()) == null
    Column(
        modifier =
            Modifier.fillMaxSize()
                .imePadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.auth_confirmation_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(stringResource(R.string.auth_confirmation_instructions))
        state.notice?.let { Text(it.resolve(context)) }
        state.error?.let { Text(it.resolve(context), color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.auth_confirmation_email)) },
            supportingText = { Text(stringResource(R.string.auth_confirmation_change_email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !state.isBusy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.auth_confirmation_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            enabled = !state.isBusy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (remaining > 0) Text(stringResource(R.string.auth_confirmation_retry_after, seconds))
        Button(
            onClick = onConfirm,
            enabled = canSend && AuthValidator.codeError(state.code) == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (state.operation == RegistrationOperation.CONFIRMING)
                        R.string.auth_confirmation_confirming
                    else R.string.auth_confirmation_submit,
                ),
            )
        }
        TextButton(onClick = onResend, enabled = canSend) {
            Text(stringResource(R.string.auth_confirmation_resend))
        }
        if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = onBackToLogin, enabled = !state.isBusy) {
            Text(stringResource(R.string.auth_confirmation_back_login))
        }
    }
}
