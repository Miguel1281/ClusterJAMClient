package com.clusterjam.mobile.features.auth.presentation.recovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
    val emailError = if (state.email.isBlank()) null else AuthValidator.emailError(state.email)
    val codeError = if (state.code.isEmpty()) null else AuthValidator.codeError(state.code)
    val passwordError =
        if (state.password.isEmpty()) null else AuthValidator.passwordError(state.password)
    val mismatch = state.confirmation.isNotEmpty() && state.password != state.confirmation
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Display only: recomposition, recreation and countdown never send a recovery request.
    LaunchedEffect(state.retryAtMillis) {
        now = System.currentTimeMillis()
        while (now < state.retryAtMillis) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = (state.retryAtMillis - now).coerceAtLeast(0)
    val canSend = !state.isBusy && remaining == 0L && AuthValidator.emailError(state.email) == null
    Column(
        modifier =
            Modifier.fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.auth_recovery_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        state.error?.let { Text(it.resolve(context), color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it.resolve(context)) }
        if (remaining > 0)
            Text(
                stringResource(
                    R.string.auth_retry_after,
                    remaining / 1000 + if (remaining % 1000 > 0) 1 else 0,
                ),
            )
        if (state.step == PasswordRecoveryStep.ENTER_CODE) {
            Text(stringResource(R.string.auth_recovery_pending_email, state.email))
            OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.auth_recovery_code)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isBusy,
                isError = codeError != null,
                supportingText = {
                    codeError?.let { Text(stringResource(AuthMessageMapper.validation(it))) }
                },
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.auth_recovery_password)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isBusy,
                isError = passwordError != null,
                supportingText = {
                    Text(
                        stringResource(
                            passwordError?.let(AuthMessageMapper::validation)
                                ?: R.string.auth_password_hint,
                        ),
                    )
                },
            )
            OutlinedTextField(
                value = state.confirmation,
                onValueChange = onConfirmationChange,
                label = { Text(stringResource(R.string.auth_recovery_confirmation)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isBusy,
                isError = mismatch,
                supportingText = {
                    if (mismatch) Text(stringResource(R.string.auth_password_mismatch))
                },
            )
            Button(
                onClick = onRecoverPassword,
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    canSend &&
                        AuthValidator.codeError(state.code) == null &&
                        AuthValidator.passwordError(state.password) == null &&
                        state.password == state.confirmation,
            ) {
                Text(
                    stringResource(
                        if (state.operation == PasswordRecoveryOperation.SUBMITTING)
                            R.string.auth_recovery_updating
                        else R.string.auth_recovery_update,
                    ),
                )
            }
            TextButton(onClick = onRequestCode, enabled = canSend) {
                Text(stringResource(R.string.auth_recovery_resend))
            }
            TextButton(onClick = onChangeEmail, enabled = !state.isBusy) {
                Text(stringResource(R.string.auth_recovery_change_email))
            }
        } else if (state.step != PasswordRecoveryStep.COMPLETED) {
            Text(stringResource(R.string.auth_recovery_instructions))
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.auth_recovery_email)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !state.isBusy,
                isError = emailError != null,
                supportingText = {
                    emailError?.let { Text(stringResource(AuthMessageMapper.validation(it))) }
                },
            )
            Button(onClick = onRequestCode, modifier = Modifier.fillMaxWidth(), enabled = canSend) {
                Text(
                    stringResource(
                        when {
                            state.operation == PasswordRecoveryOperation.REQUESTING ->
                                R.string.auth_recovery_requesting
                            state.step == PasswordRecoveryStep.NEW_CODE_REQUIRED ->
                                R.string.auth_recovery_resend
                            else -> R.string.auth_recovery_request
                        },
                    ),
                )
            }
        }
        if (state.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onBack, enabled = !state.isBusy) {
            Text(stringResource(R.string.auth_recovery_back))
        }
        Spacer(Modifier.height(24.dp))
    }
}
