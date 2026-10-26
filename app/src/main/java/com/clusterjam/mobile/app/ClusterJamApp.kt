package com.clusterjam.mobile.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.clusterjam.mobile.core.ui.theme.ClusterJAMClientTheme
import com.clusterjam.mobile.features.auth.presentation.AuthViewModel

@Composable
fun ClusterJamApp(authViewModel: AuthViewModel) {
    ClusterJAMClientTheme {
        val state by authViewModel.uiState.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        DisposableEffect(lifecycleOwner, authViewModel) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START &&
                    authViewModel.uiState.value.identity != null
                ) {
                    authViewModel.refreshIdentity()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (state.isRestoring || state.startupError != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.auth_checking_session))
                    } else {
                        state.startupError?.let { Text(it.resolve(context)) }
                        Button(onClick = authViewModel::restoreSession) {
                            Text(stringResource(R.string.auth_retry))
                        }
                        TextButton(onClick = authViewModel::logout) {
                            Text(stringResource(R.string.auth_logout_device))
                        }
                    }
                }
            } else {
                key(state.identity?.userId) { AppNavHost(state, authViewModel) }
            }
        }
    }
}
