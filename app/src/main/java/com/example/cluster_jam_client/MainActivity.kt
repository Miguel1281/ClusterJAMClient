package com.example.cluster_jam_client

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cluster_jam_client.presentation.auth.*
import com.example.cluster_jam_client.presentation.catalog.CatalogScreen
import com.example.cluster_jam_client.presentation.profile.ProfileScreen
import com.example.cluster_jam_client.ui.theme.ClusterJAMClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClusterJAMClientTheme {
                val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(applicationContext))
                val state by authViewModel.uiState.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, authViewModel) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START && authViewModel.uiState.value.identity != null) {
                            authViewModel.refreshIdentity()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                    if (state.isRestoring || state.startupError != null) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator()
                                Text("Comprobando sesión…")
                            } else {
                                Text(state.startupError ?: "No se pudo comprobar la sesión.")
                                Button(onClick = authViewModel::restoreSession) { Text("Volver a intentar") }
                                TextButton(onClick = authViewModel::logout) { Text("Cerrar sesión en este dispositivo") }
                            }
                        }
                    } else {
                        // A change of account recreates the graph and discards its previous back stack.
                        key(state.identity?.userId) {
                            val navController = rememberNavController()
                            fun returnToLogin() {
                                navController.navigate("login") {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            LaunchedEffect(authViewModel, navController) {
                                authViewModel.authEvent.collect { event ->
                                    when (event) {
                                        is AuthEvent.Notice -> {
                                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG).show()
                                            if (event.returnToLogin) returnToLogin()
                                        }
                                    }
                                }
                            }
                            BackHandler(enabled = state.isLoading) { }
                            NavHost(
                                navController = navController,
                                startDestination = if (state.identity != null) "catalog" else "login"
                            ) {
                                composable("login") {
                                    LoginScreen(
                                        onNavigateToRegister = { navController.navigate("register") },
                                        onLoginClick = authViewModel::login,
                                        onForgotPasswordClick = {
                                            authViewModel.beginRecovery()
                                            navController.navigate("recovery")
                                        },
                                        isLoading = state.isLoading
                                    )
                                }
                                composable("register") {
                                    RegisterScreen(
                                        onNavigateToLogin = ::returnToLogin,
                                        onRegisterClick = authViewModel::register,
                                        isLoading = state.isLoading
                                    )
                                }
                                composable("recovery") {
                                    PasswordRecoveryScreen(
                                        recoveryEmail = state.recoveryEmail,
                                        initialEmail = state.identity?.email.orEmpty(),
                                        isLoading = state.isLoading,
                                        onRequestCode = authViewModel::requestPasswordRecovery,
                                        onRecoverPassword = authViewModel::recoverPassword,
                                        onChangeEmail = authViewModel::changeRecoveryEmail,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("catalog") {
                                    if (state.identity != null) {
                                        CatalogScreen(onNavigateToProfile = { navController.navigate("profile") })
                                    } else {
                                        LaunchedEffect(Unit) { returnToLogin() }
                                    }
                                }
                                composable("profile") {
                                    if (state.identity != null) {
                                        ProfileScreen(
                                            onNavigateToCatalog = { navController.popBackStack("catalog", false) },
                                            onLogoutClick = authViewModel::logout,
                                            accessEmail = state.identity?.email.orEmpty(),
                                            isLoading = state.isLoading,
                                            onRecoverPasswordClick = {
                                                authViewModel.beginRecovery()
                                                navController.navigate("recovery")
                                            },
                                            onRefreshIdentityClick = authViewModel::refreshIdentity,
                                            onCheckHealthClick = authViewModel::checkHealth
                                        )
                                    } else {
                                        LaunchedEffect(Unit) { returnToLogin() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
