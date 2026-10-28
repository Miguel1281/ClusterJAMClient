package com.clusterjam.mobile.features.auth.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.*
import com.clusterjam.mobile.core.ui.theme.JamSpacing
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import kotlinx.coroutines.delay

/** The app root owns safe system insets. This is the only IME-inset owner for forms. */
@Composable
fun AuthForm(
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
    feedback: @Composable ColumnScope.() -> Unit = {},
    feedbackKey: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val feedbackHeight = maxHeight * 0.32f
        val feedbackScroll = rememberScrollState()
        LaunchedEffect(feedbackKey) { feedbackScroll.scrollTo(0) }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.widthIn(max = JamSpacing.FormWidth)
                                .fillMaxWidth()
                                .padding(horizontal = JamSpacing.Page)
                                .padding(top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Long messages remain readable at large font sizes, above the action.
                            Column(
                                Modifier.fillMaxWidth()
                                    .heightIn(max = feedbackHeight)
                                    .verticalScroll(feedbackScroll),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                content = feedback,
                            )
                            action()
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier.widthIn(max = JamSpacing.FormWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(JamSpacing.Page),
                    verticalArrangement = Arrangement.spacedBy(JamSpacing.Medium),
                ) {
                    BrandHeader()
                    Spacer(Modifier.height(8.dp))
                    ScreenHeading(title, subtitle)
                    Spacer(Modifier.height(4.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    error: String? = null,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) bringIntoView.bringIntoView() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        isError = error != null,
        supportingText =
            if (error != null || hint != null) {
                { Text(error ?: hint.orEmpty()) }
            } else null,
        visualTransformation =
            if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon =
            if (password) {
                {
                    IconButton(onClick = { visible = !visible }, enabled = enabled) {
                        Icon(
                            painterResource(
                                if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye,
                            ),
                            contentDescription =
                                stringResource(
                                    if (visible) R.string.auth_hide_password
                                    else R.string.auth_show_password,
                                ),
                        )
                    }
                }
            } else null,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
            ),
        keyboardActions =
            KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Next) },
                onDone = {
                    if (enabled) {
                        keyboard?.hide()
                        focus.clearFocus()
                        onDone?.invoke()
                    }
                },
            ),
        shape = MaterialTheme.shapes.medium,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView),
    )
}

enum class AuthFieldKind {
    Email,
    Password,
    Code,
}

/** Classify existing mapped messages only; business error codes stay in AuthMessageMapper. */
fun AuthMessage.fieldKind(): AuthFieldKind? =
    when (resourceId) {
        R.string.auth_error_invalid_email,
        R.string.auth_error_email_registered,
        R.string.auth_validation_email -> AuthFieldKind.Email
        R.string.auth_error_weak_password,
        R.string.auth_validation_password_required,
        R.string.auth_validation_password_short -> AuthFieldKind.Password
        R.string.auth_error_confirmation_code,
        R.string.auth_error_recovery_code,
        R.string.auth_validation_code -> AuthFieldKind.Code
        else -> null
    }

@Composable
fun fieldMessage(message: AuthMessage?, kind: AuthFieldKind): String? =
    message?.takeIf { it.fieldKind() == kind }?.resolve(LocalContext.current)

@Composable
fun AuthFeedback(
    error: AuthMessage? = null,
    notice: AuthMessage? = null,
    success: Boolean = false,
    remainingSeconds: Long = 0,
    action: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    when {
        error != null -> StatusMessage(error.resolve(context), FeedbackTone.Error, action)
        notice != null ->
            StatusMessage(
                notice.resolve(context),
                if (success) FeedbackTone.Success else FeedbackTone.Info,
                action,
            )
    }
    if (remainingSeconds > 0) {
        Text(
            stringResource(R.string.auth_retry_after, remainingSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Display clock only. Never requests, resends or validates a server code. */
@Composable
fun rememberRetrySeconds(retryAtMillis: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(retryAtMillis) {
        now = System.currentTimeMillis()
        while (now < retryAtMillis) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val millis = (retryAtMillis - now).coerceAtLeast(0)
    return millis / 1000 + if (millis % 1000 > 0) 1 else 0
}
