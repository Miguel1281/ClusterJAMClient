package com.clusterjam.mobile.features.profile.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.PrimaryButton
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.auth.presentation.components.AuthFeedback
import com.clusterjam.mobile.features.auth.presentation.components.AuthField
import com.clusterjam.mobile.features.auth.presentation.components.AuthForm
import com.clusterjam.mobile.features.auth.presentation.components.rememberRetrySeconds
import com.clusterjam.mobile.features.profile.domain.model.OnboardingStatus
import com.clusterjam.mobile.features.profile.domain.validation.ProfileValidationError

@Composable
fun ProfileFormScreen(
    state: ProfileFormUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onKeepDraft: () -> Unit,
    onUseServer: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    var discard by remember { mutableStateOf(false) }
    fun leave() {
        if (!state.isBusy) {
            if (state.dirty) discard = true else onBack()
        }
    }
    BackHandler { leave() }
    val remaining = rememberRetrySeconds(state.retryAtMillis)
    val completed = state.snapshot?.profile?.onboardingStatus == OnboardingStatus.COMPLETED
    AuthForm(
        title =
            stringResource(
                if (state.mode == ProfileFormMode.ONBOARDING) R.string.profile_onboarding_title
                else R.string.profile_edit_title,
            ),
        subtitle = stringResource(R.string.profile_form_subtitle),
        feedbackKey = state.error ?: state.needsReview,
        action = {
            PrimaryButton(
                stringResource(
                    when {
                        state.saved -> R.string.profile_continue
                        state.isSaving -> R.string.profile_saving
                        else -> R.string.profile_save
                    },
                ),
                onClick = if (state.saved) onContinue else onSave,
                enabled = if (state.saved) completed else state.canSave && remaining == 0L,
                loading = state.isSaving,
            )
            TextButton(onClick = ::leave, enabled = !state.isBusy) {
                Text(stringResource(R.string.profile_back))
            }
        },
        feedback = {
            AuthFeedback(
                error = state.error,
                notice =
                    if (state.saved)
                        AuthMessage(
                            if (completed) R.string.profile_saved
                            else R.string.profile_saved_pending,
                        )
                    else null,
                success = state.saved,
                remainingSeconds = remaining,
            )
            if (state.needsReload && !state.isLoading)
                TextButton(onClick = onReload, enabled = !state.isBusy && remaining == 0L) {
                    Text(stringResource(R.string.profile_reload))
                }
            if (state.needsReview) {
                Text(stringResource(R.string.profile_review_required))
                state.snapshot?.profile?.let {
                    Text(
                        stringResource(
                            R.string.profile_server_values,
                            it.fullName.orEmpty(),
                            it.phone.orEmpty(),
                            it.contactEmail.orEmpty(),
                        ),
                    )
                }
                TextButton(onClick = onKeepDraft, enabled = !state.isBusy) {
                    Text(stringResource(R.string.profile_keep_draft))
                }
                TextButton(onClick = onUseServer, enabled = !state.isBusy) {
                    Text(stringResource(R.string.profile_use_server))
                }
            }
        },
    ) {
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val enabled = state.isOpen && !state.isBusy && !state.saved
        AuthField(
            state.fullName,
            onNameChange,
            stringResource(R.string.profile_full_name),
            enabled = enabled,
            error =
                if (state.validationError == ProfileValidationError.INVALID_NAME)
                    stringResource(R.string.profile_validation_name)
                else null,
        )
        AuthField(
            state.phone,
            onPhoneChange,
            stringResource(R.string.profile_phone),
            enabled = enabled,
            keyboardType = KeyboardType.Phone,
            error =
                if (state.validationError == ProfileValidationError.INVALID_PHONE)
                    stringResource(R.string.profile_validation_phone)
                else null,
        )
        AuthField(
            state.contactEmail,
            onEmailChange,
            stringResource(R.string.profile_contact_email),
            enabled = enabled,
            keyboardType = KeyboardType.Email,
            hint = stringResource(R.string.profile_contact_hint),
            error =
                if (state.validationError == ProfileValidationError.INVALID_CONTACT_EMAIL)
                    stringResource(R.string.auth_validation_email)
                else null,
            onDone = { if (state.canSave && remaining == 0L) onSave() },
        )
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.profile_discard_title)) },
            text = { Text(stringResource(R.string.profile_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.profile_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) {
                    Text(stringResource(R.string.profile_keep_editing))
                }
            },
        )
}
