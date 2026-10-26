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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clusterjam.mobile.features.auth.presentation.AuthEvent
import com.clusterjam.mobile.features.auth.presentation.AuthUiState
import com.clusterjam.mobile.features.auth.presentation.AuthViewModel
import com.clusterjam.mobile.features.auth.presentation.login.LoginScreen
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryScreen
import com.clusterjam.mobile.features.auth.presentation.registration.RegisterScreen
import com.clusterjam.mobile.features.catalog.presentation.CatalogScreen
import com.clusterjam.mobile.features.profile.presentation.ProfileScreen

@Composable
fun AppNavHost(state: AuthUiState, authViewModel: AuthViewModel) {
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
                        context, event.message.resolve(context), Toast.LENGTH_LONG
                    ).show()
                    if (event.returnToLogin) returnToLogin()
                }
            }
        }
    }
    BackHandler(enabled = state.isLoading) { }
    MainScaffold(
        currentRoute = backStackEntry?.destination?.route,
        onNavigateToCatalog = { navController.popBackStack(Routes.CATALOG, false) },
        onNavigateToProfile = {
            navController.navigate(Routes.PROFILE) { launchSingleTop = true }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = if (state.identity != null) Routes.CATALOG else Routes.LOGIN,
            modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                    onLoginClick = authViewModel::login,
                    onForgotPasswordClick = {
                        authViewModel.beginRecovery()
                        navController.navigate(Routes.RECOVERY)
                    },
                    isLoading = state.isLoading
                )
            }
            composable(Routes.REGISTER) {
                RegisterScreen(
                    onNavigateToLogin = ::returnToLogin,
                    onRegisterClick = authViewModel::register,
                    isLoading = state.isLoading
                )
            }
            composable(Routes.RECOVERY) {
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
            composable(Routes.CATALOG) {
                if (state.identity != null) {
                    CatalogScreen()
                } else {
                    LaunchedEffect(Unit) { returnToLogin() }
                }
            }
            composable(Routes.PROFILE) {
                if (state.identity != null) {
                    ProfileScreen(
                        onLogoutClick = authViewModel::logout,
                        accessEmail = state.identity.email,
                        isLoading = state.isLoading,
                        onRecoverPasswordClick = {
                            authViewModel.beginRecovery()
                            navController.navigate(Routes.RECOVERY)
                        },
                        onRefreshIdentityClick = authViewModel::refreshIdentity
                    )
                } else {
                    LaunchedEffect(Unit) { returnToLogin() }
                }
            }
        }
    }
}
