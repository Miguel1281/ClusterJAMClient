package com.example.cluster_jam_client.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cluster_jam_client.domain.auth.AuthValidation

@Composable
fun PasswordRecoveryScreen(
    recoveryEmail: String?,
    initialEmail: String,
    isLoading: Boolean,
    onRequestCode: (String) -> Unit,
    onRecoverPassword: (String, String) -> Unit,
    onChangeEmail: () -> Unit,
    onBack: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmationError by remember { mutableStateOf<String?>(null) }
    val emailError = if (email.isBlank()) null else AuthValidation.emailError(email.trim())

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Recuperar contraseña", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (recoveryEmail == null) {
            Text("Ingresa tu correo de acceso para solicitar un código de recuperación.")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo de acceso") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !isLoading,
                isError = emailError != null,
                supportingText = { emailError?.let { Text(it) } }
            )
            Button(
                onClick = { onRequestCode(email.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && AuthValidation.emailError(email.trim()) == null
            ) { Text(if (isLoading) "Solicitando…" else "Solicitar código") }
        } else {
            Text("Si la cuenta puede recibir correos, recibirás un código en $recoveryEmail.")
            OutlinedTextField(
                value = code,
                onValueChange = { value ->
                    code = value.filter { it in '0'..'9' }.take(6)
                    codeError = AuthValidation.codeError(code)
                },
                label = { Text("Código de seis dígitos") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                isError = codeError != null,
                supportingText = { codeError?.let { Text(it) } }
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = AuthValidation.passwordError(it)
                    confirmationError = if (confirmation.isNotEmpty() && confirmation != it) "Las contraseñas no coinciden." else null
                },
                label = { Text("Nueva contraseña") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                isError = passwordError != null,
                supportingText = { Text(passwordError ?: "Al menos 8 caracteres.") }
            )
            OutlinedTextField(
                value = confirmation,
                onValueChange = {
                    confirmation = it
                    confirmationError = if (it != password) "Las contraseñas no coinciden." else null
                },
                label = { Text("Confirmar nueva contraseña") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                isError = confirmationError != null,
                supportingText = { confirmationError?.let { Text(it) } }
            )
            Button(
                onClick = { onRecoverPassword(code, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && AuthValidation.codeError(code) == null &&
                        AuthValidation.passwordError(password) == null && password == confirmation
            ) { Text(if (isLoading) "Actualizando…" else "Actualizar contraseña") }
            Text("Si el código expiró o ya se utilizó, solicita uno nuevo.", style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = { code = ""; codeError = null; onRequestCode(recoveryEmail) },
                enabled = !isLoading
            ) { Text("Solicitar otro código") }
            TextButton(onClick = onChangeEmail, enabled = !isLoading) { Text("Usar otro correo") }
        }
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onBack, enabled = !isLoading) { Text("Volver") }
        Spacer(Modifier.height(24.dp))
    }
}
