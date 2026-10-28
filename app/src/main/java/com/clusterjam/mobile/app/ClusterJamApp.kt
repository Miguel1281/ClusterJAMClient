package com.clusterjam.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clusterjam.mobile.R
import com.clusterjam.mobile.app.navigation.AppNavHost
import com.clusterjam.mobile.core.ui.components.BrandHeader
import com.clusterjam.mobile.core.ui.components.FeedbackTone
import com.clusterjam.mobile.core.ui.components.StatusMessage
import com.clusterjam.mobile.core.ui.theme.ClusterJAMClientTheme
import com.clusterjam.mobile.core.ui.theme.JamSpacing
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.presentation.AuthMessageMapper
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryStep
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryViewModel
import com.clusterjam.mobile.features.auth.presentation.registration.RegistrationViewModel
import kotlinx.coroutines.delay

@Composable
fun ClusterJamApp(
    appViewModel: AppViewModel,
    recoveryViewModel: PasswordRecoveryViewModel,
    loginViewModel: LoginViewModel,
    registrationViewModel: RegistrationViewModel,
) {
    val state by appViewModel.uiState.collectAsStateWithLifecycle()
    val recoveryState by recoveryViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appViewModel.onForeground()
                Lifecycle.Event.ON_STOP -> appViewModel.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun discardSession() {
        loginViewModel.clearError()
        appViewModel.discardSession()
    }
    ClusterJAMClientTheme {
        Surface(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    ),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val noticeHeight = maxHeight * 0.25f
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = noticeHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (recoveryState.step == PasswordRecoveryStep.COMPLETED) {
                            val cleanupPending =
                                recoveryState.cleanupFailure != null &&
                                    state.session is SessionState.AccessDenied
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.widthIn(max = JamSpacing.FormWidth)) {
                                    StatusMessage(
                                        message =
                                            stringResource(
                                                if (cleanupPending)
                                                    R.string.auth_password_updated_cleanup
                                                else R.string.auth_password_updated,
                                            ),
                                        tone =
                                            if (cleanupPending) FeedbackTone.Info
                                            else FeedbackTone.Success,
                                        action = {
                                            TextButton(
                                                onClick = recoveryViewModel::dismissCompletion,
                                            ) {
                                                Text(stringResource(R.string.auth_dismiss_notice))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        state.notice?.let {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.widthIn(max = JamSpacing.FormWidth)) {
                                    StatusMessage(
                                        it.resolve(LocalContext.current),
                                        tone =
                                            if (it.resourceId == R.string.auth_logged_out)
                                                FeedbackTone.Success
                                            else FeedbackTone.Info,
                                        action = {
                                            TextButton(onClick = appViewModel::dismissNotice) {
                                                Text(stringResource(R.string.auth_dismiss_notice))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    when (val session = state.session) {
                        SessionState.Checking -> {
                            BackHandler {}
                            SessionStatus {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.auth_checking_session))
                            }
                        }
                        is SessionState.AccessDenied -> {
                            BackHandler(enabled = !state.isBusy, onBack = ::discardSession)
                            SessionStatus {
                                Text(
                                    stringResource(R.string.auth_access_denied),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                StatusMessage(
                                    AuthMessageMapper.failure(session.failure)
                                        .resolve(LocalContext.current),
                                    FeedbackTone.Error,
                                )
                                session.cleanupFailure?.let {
                                    StatusMessage(
                                        AuthMessageMapper.failure(it).resolve(LocalContext.current),
                                        FeedbackTone.Error,
                                    )
                                }
                                Button(
                                    onClick = ::discardSession,
                                    enabled = !state.isBusy,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .heightIn(min = JamSpacing.ButtonHeight),
                                ) {
                                    Text(
                                        stringResource(
                                            if (
                                                recoveryState.step ==
                                                    PasswordRecoveryStep.COMPLETED &&
                                                    recoveryState.cleanupFailure != null
                                            )
                                                R.string.auth_retry_local_cleanup
                                            else R.string.auth_use_another_account,
                                        ),
                                    )
                                }
                            }
                        }
                        is SessionState.RecoverableError -> {
                            var remaining by
                                remember(session.failure) {
                                    mutableLongStateOf(session.failure.retryAfterMillis ?: 0L)
                                }
                            LaunchedEffect(session.failure) {
                                val duration = remaining.coerceAtLeast(0)
                                val start = System.currentTimeMillis()
                                while (remaining > 0) {
                                    delay(minOf(remaining, 1000L))
                                    remaining =
                                        (duration - (System.currentTimeMillis() - start))
                                            .coerceAtLeast(
                                                0,
                                            )
                                }
                            }
                            BackHandler(enabled = !state.isBusy, onBack = ::discardSession)
                            SessionStatus {
                                StatusMessage(
                                    AuthMessageMapper.failure(session.failure)
                                        .resolve(LocalContext.current),
                                    FeedbackTone.Error,
                                )
                                if (remaining > 0) {
                                    Text(
                                        stringResource(
                                            R.string.auth_retry_after,
                                            remaining / 1000 + if (remaining % 1000 > 0) 1 else 0,
                                        ),
                                    )
                                }
                                Button(
                                    onClick = appViewModel::retryIdentity,
                                    enabled = !state.isBusy && remaining == 0L,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .heightIn(min = JamSpacing.ButtonHeight),
                                ) {
                                    Text(stringResource(R.string.auth_retry))
                                }
                                TextButton(onClick = ::discardSession, enabled = !state.isBusy) {
                                    Text(stringResource(R.string.auth_logout_device))
                                }
                            }
                        }
                        SessionState.Unauthenticated,
                        is SessionState.Authenticated -> {
                            val identity = (session as? SessionState.Authenticated)?.identity
                            // A denied/unverified identity removes this entire graph and its back
                            // stack.
                            key(identity?.userId) {
                                AppNavHost(
                                    identity = identity,
                                    recoveryViewModel = recoveryViewModel,
                                    loginViewModel = loginViewModel,
                                    registrationViewModel = registrationViewModel,
                                    isSessionBusy = state.isBusy,
                                    onLogout = {
                                        loginViewModel.clearError()
                                        appViewModel.logout()
                                    },
                                    onRefreshIdentity = appViewModel::refreshIdentity,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionStatus(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier.widthIn(max = JamSpacing.FormWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            BrandHeader()
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
