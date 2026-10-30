package com.clusterjam.mobile.features.profile.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.PrimaryButton
import com.clusterjam.mobile.features.auth.presentation.components.AuthFeedback
import com.clusterjam.mobile.features.auth.presentation.components.AuthForm

@Composable
fun ProfileGateScreen(
    state: ProfileUiState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onRefreshIdentity: () -> Unit,
) {
    AuthForm(
        title = stringResource(R.string.profile_gate_title),
        subtitle = stringResource(R.string.profile_gate_subtitle),
        feedbackKey = state.error ?: state.status,
        feedback = { AuthFeedback(error = state.error) },
        action = {
            PrimaryButton(stringResource(R.string.auth_retry), onRetry, enabled = !state.isBusy)
            TextButton(onClick = onRefreshIdentity, enabled = !state.isBusy) {
                Text(stringResource(R.string.profile_check_session))
            }
            TextButton(onClick = onLogout) { Text(stringResource(R.string.auth_logout_device)) }
        },
    ) {
        if (state.isBusy || state.status == ProfileLoadStatus.IDLE)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            stringResource(
                if (state.status == ProfileLoadStatus.PROVISIONING) {
                    if (state.automaticRetry) R.string.profile_provisioning_retry
                    else R.string.profile_provisioning
                } else if (state.status == ProfileLoadStatus.ERROR) R.string.profile_gate_error
                else R.string.profile_loading,
            ),
        )
    }
}
