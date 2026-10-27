package com.clusterjam.mobile.app.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clusterjam.mobile.features.auth.domain.model.VerifiedIdentity
import com.clusterjam.mobile.features.auth.presentation.AuthEvent
import com.clusterjam.mobile.features.auth.presentation.AuthViewModel
import com.clusterjam.mobile.features.auth.presentation.login.LoginScreen
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryScreen
import com.clusterjam.mobile.features.auth.presentation.registration.RegisterScreen
import com.clusterjam.mobile.features.catalog.presentation.CatalogScreen
import com.clusterjam.mobile.features.profile.presentation.ProfileScreen

@Composable
fun AppNavHost(
    identity: VerifiedIdentity?,
    authViewModel: AuthViewModel,
    loginViewModel: LoginViewModel,
    isSessionBusy: Boolean,
    onLogout: () -> Unit,
    onRefreshIdentity: () -> Unit,
) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()

    fun returnToLogin() {
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(authViewModel, navController) {
        authViewModel.authEvent.collect { event ->
            when (event) {
                is AuthEvent.Notice -> {
                    Toast.makeText(
                            context,
                            event.message.resolve(context),
                            Toast.LENGTH_LONG,
                        )
                        .show()
                    if (event.returnToLogin && identity == null) returnToLogin()
                }
            }
        }
    }
    BackHandler(enabled = state.isLoading || loginState.isLoading) {}
    MainScaffold(
        currentRoute = backStackEntry?.destination?.route,
        onNavigateToCatalog = { navController.popBackStack(Routes.CATALOG, false) },
        onNavigateToProfile = {
            navController.navigate(Routes.PROFILE) { launchSingleTop = true }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = if (identity != null) Routes.CATALOG else Routes.LOGIN,
            modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
        ) {
            if (identity == null) {
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                        onLoginClick = loginViewModel::login,
                        onForgotPasswordClick = {
                            authViewModel.beginRecovery()
                            navController.navigate(Routes.RECOVERY)
                        },
                        state = loginState,
                        onEmailChange = loginViewModel::changeEmail,
                        onPasswordChange = loginViewModel::changePassword,
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(
                        onNavigateToLogin = ::returnToLogin,
                        onRegisterClick = authViewModel::register,
                        isLoading = state.isLoading,
                        error = state.error,
                    )
                }
            }
            composable(Routes.RECOVERY) {
                PasswordRecoveryScreen(
                    recoveryEmail = state.recoveryEmail,
                    initialEmail = identity?.email.orEmpty(),
                    isLoading = state.isLoading,
                    onRequestCode = authViewModel::requestPasswordRecovery,
                    onRecoverPassword = authViewModel::recoverPassword,
                    onChangeEmail = authViewModel::changeRecoveryEmail,
                    onBack = { navController.popBackStack() },
                    error = state.error,
                )
            }
            if (identity != null) {
                composable(Routes.CATALOG) {
                    CatalogScreen()
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onLogoutClick = onLogout,
                        accessEmail = identity.email,
                        isLoading = state.isLoading || isSessionBusy,
                        onRecoverPasswordClick = {
                            authViewModel.beginRecovery()
                            navController.navigate(Routes.RECOVERY)
                        },
                        onRefreshIdentityClick = onRefreshIdentity,
                    )
                }
            }
        }
    }
}
