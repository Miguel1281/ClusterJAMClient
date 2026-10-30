package com.clusterjam.mobile.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clusterjam.mobile.ClusterJamApplication
import com.clusterjam.mobile.app.di.ViewModelFactories
import com.clusterjam.mobile.features.auth.domain.model.VerifiedIdentity
import com.clusterjam.mobile.features.auth.presentation.login.LoginScreen
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryScreen
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryStep
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryViewModel
import com.clusterjam.mobile.features.auth.presentation.registration.EmailConfirmationScreen
import com.clusterjam.mobile.features.auth.presentation.registration.RegisterScreen
import com.clusterjam.mobile.features.auth.presentation.registration.RegistrationStep
import com.clusterjam.mobile.features.auth.presentation.registration.RegistrationViewModel
import com.clusterjam.mobile.features.catalog.presentation.CatalogScreen
import com.clusterjam.mobile.features.profile.domain.model.OnboardingStatus
import com.clusterjam.mobile.features.profile.presentation.ProfileFormMode
import com.clusterjam.mobile.features.profile.presentation.ProfileFormScreen
import com.clusterjam.mobile.features.profile.presentation.ProfileFormUiState
import com.clusterjam.mobile.features.profile.presentation.ProfileFormViewModel
import com.clusterjam.mobile.features.profile.presentation.ProfileGateScreen
import com.clusterjam.mobile.features.profile.presentation.ProfileLoadStatus
import com.clusterjam.mobile.features.profile.presentation.ProfileScreen
import com.clusterjam.mobile.features.profile.presentation.ProfileUiState
import com.clusterjam.mobile.features.profile.presentation.ProfileViewModel
import com.clusterjam.mobile.features.profile.presentation.addresses.AddressFormScreen
import com.clusterjam.mobile.features.profile.presentation.addresses.AddressListScreen
import com.clusterjam.mobile.features.profile.presentation.addresses.AddressStage
import com.clusterjam.mobile.features.profile.presentation.addresses.AddressUiState
import com.clusterjam.mobile.features.profile.presentation.addresses.AddressViewModel

@Composable
fun AppNavHost(
    identity: VerifiedIdentity?,
    recoveryViewModel: PasswordRecoveryViewModel,
    loginViewModel: LoginViewModel,
    registrationViewModel: RegistrationViewModel,
    isSessionBusy: Boolean,
    onLogout: () -> Unit,
    onRefreshIdentity: () -> Unit,
) {
    val recoveryState by recoveryViewModel.uiState.collectAsStateWithLifecycle()
    val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val registrationState by registrationViewModel.uiState.collectAsStateWithLifecycle()
    val factory =
        if (identity != null) {
            val container =
                (LocalContext.current.applicationContext as ClusterJamApplication).container
            remember(container) {
                ViewModelFactories(container.authRepository, container.profileRepository)
            }
        } else null
    // Like Auth's form ViewModels, these survive Activity/navigation-graph recreation.
    val profileViewModel: ProfileViewModel? =
        if (factory != null) viewModel(factory = factory) else null
    val formViewModel: ProfileFormViewModel? =
        if (factory != null) viewModel(factory = factory) else null
    val addressViewModel: AddressViewModel? =
        if (factory != null) viewModel(factory = factory) else null
    val addressState =
        addressViewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: AddressUiState()
    val profileState =
        profileViewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: ProfileUiState()
    val formState =
        formViewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: ProfileFormUiState()
    LaunchedEffect(formState.snapshot?.revision, formState.isBusy, formState.needsReload) {
        if (!formState.isBusy && !formState.needsReload) {
            formState.snapshot?.let { profileViewModel?.acceptServerProfile(it) }
        }
    }
    if (profileViewModel != null)
        ProfileLifecycle(profileViewModel::onVisible, profileViewModel::onHidden)
    val profileReady =
        profileState.status == ProfileLoadStatus.READY && profileState.snapshot != null
    val completed =
        profileReady &&
            profileState.snapshot?.profile?.onboardingStatus == OnboardingStatus.COMPLETED
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    if (
        addressViewModel != null &&
            profileReady &&
            backStackEntry?.destination?.route in setOf(Routes.ADDRESSES, Routes.ADDRESS_FORM)
    ) {
        ProfileLifecycle(addressViewModel::onVisible, addressViewModel::onHidden)
    }
    val startDestination = remember {
        when {
            identity != null -> Routes.PROFILE_GATE
            registrationState.step == RegistrationStep.WAITING_CODE -> Routes.EMAIL_CONFIRMATION
            else -> Routes.LOGIN
        }
    }

    fun showRoot(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
    fun openForm(mode: ProfileFormMode) {
        formViewModel?.begin(mode)
        navController.navigate(
            if (mode == ProfileFormMode.ONBOARDING) Routes.ONBOARDING else Routes.EDIT_PROFILE,
        ) {
            launchSingleTop = true
        }
    }
    LaunchedEffect(
        backStackEntry?.destination?.route,
        profileState.snapshot?.revision,
        formState.isOpen,
        formState.saved,
        formState.isBusy,
        addressState.stage,
        addressState.isWriting,
    ) {
        if (identity != null && profileReady) {
            val route = backStackEntry?.destination?.route
            if (route == Routes.PROFILE_GATE) {
                when {
                    addressState.stage != AddressStage.LIST -> showRoot(Routes.ADDRESS_FORM)
                    formState.isOpen &&
                        (formState.dirty ||
                            !completed ||
                            formState.mode == ProfileFormMode.EDIT) -> {
                        val mode =
                            if (completed) ProfileFormMode.EDIT else ProfileFormMode.ONBOARDING
                        formViewModel?.resumeMode(mode)
                        showRoot(
                            if (mode == ProfileFormMode.EDIT) Routes.EDIT_PROFILE
                            else Routes.ONBOARDING,
                        )
                    }
                    completed -> {
                        formViewModel?.close()
                        showRoot(Routes.CATALOG)
                    }
                    else -> {
                        formViewModel?.begin(ProfileFormMode.ONBOARDING)
                        showRoot(Routes.ONBOARDING)
                    }
                }
            } else if (route == Routes.ADDRESSES && addressState.stage != AddressStage.LIST) {
                navController.navigate(Routes.ADDRESS_FORM) { launchSingleTop = true }
            } else if (
                route == Routes.ADDRESS_FORM &&
                    addressState.stage == AddressStage.LIST &&
                    !addressState.isWriting
            ) {
                showRoot(Routes.ADDRESSES)
            } else if (route == Routes.CATALOG && !completed) {
                formViewModel?.begin(ProfileFormMode.ONBOARDING)
                showRoot(Routes.ONBOARDING)
            } else if (
                route == Routes.ONBOARDING && completed && !formState.saved && !formState.isBusy
            ) {
                if (formState.isOpen && formState.dirty) {
                    formViewModel?.resumeMode(ProfileFormMode.EDIT)
                    showRoot(Routes.EDIT_PROFILE)
                } else {
                    formViewModel?.close()
                    showRoot(Routes.CATALOG)
                }
            } else if (
                route == Routes.EDIT_PROFILE &&
                    !completed &&
                    formState.isOpen &&
                    !formState.saved &&
                    !formState.isBusy
            ) {
                formViewModel?.resumeMode(ProfileFormMode.ONBOARDING)
                showRoot(Routes.ONBOARDING)
            } else if (
                route in setOf(Routes.ONBOARDING, Routes.EDIT_PROFILE) &&
                    !formState.isOpen &&
                    !formState.saved
            ) {
                showRoot(if (completed) Routes.PROFILE else Routes.PROFILE_GATE)
            }
        }
    }

    fun returnToLogin() {
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(recoveryState.step, backStackEntry?.destination?.route) {
        if (
            identity == null &&
                recoveryState.step == PasswordRecoveryStep.COMPLETED &&
                backStackEntry?.destination?.route == Routes.RECOVERY
        ) {
            returnToLogin()
        }
    }
    LaunchedEffect(registrationState.step, backStackEntry?.destination?.route) {
        if (identity == null) {
            val route = backStackEntry?.destination?.route
            if (
                registrationState.step == RegistrationStep.WAITING_CODE && route == Routes.REGISTER
            ) {
                navController.navigate(Routes.EMAIL_CONFIRMATION) {
                    popUpTo(Routes.REGISTER) { inclusive = true }
                    launchSingleTop = true
                }
            } else if (
                registrationState.step == RegistrationStep.COMPLETED &&
                    route in setOf(Routes.REGISTER, Routes.EMAIL_CONFIRMATION)
            ) {
                returnToLogin()
            }
        }
    }
    BackHandler(
        enabled = recoveryState.isBusy || loginState.isLoading || registrationState.isBusy,
    ) {}
    MainScaffold(
        currentRoute =
            backStackEntry?.destination?.route.takeIf { identity == null || profileReady },
        catalogEnabled = completed,
        onNavigateToCatalog = { if (completed) showRoot(Routes.CATALOG) },
        onNavigateToProfile = {
            navController.navigate(Routes.PROFILE) { launchSingleTop = true }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
        ) {
            if (identity == null) {
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onNavigateToRegister = {
                            registrationViewModel.startRegistration(loginState.email)
                            navController.navigate(Routes.REGISTER)
                        },
                        onLoginClick = {
                            registrationViewModel.clearNotice()
                            if (recoveryState.step == PasswordRecoveryStep.COMPLETED)
                                recoveryViewModel.dismissCompletion()
                            loginViewModel.login()
                        },
                        onEmailConfirmationClick = {
                            loginState.emailAwaitingConfirmation?.let { email ->
                                registrationViewModel.beginConfirmation(email)
                                loginViewModel.clearError()
                                navController.navigate(Routes.EMAIL_CONFIRMATION) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        notice =
                            registrationState.notice.takeIf {
                                registrationState.step == RegistrationStep.COMPLETED
                            },
                        onForgotPasswordClick = {
                            loginViewModel.clearError()
                            registrationViewModel.clearNotice()
                            recoveryViewModel.begin(loginState.email)
                            navController.navigate(Routes.RECOVERY)
                        },
                        state = loginState,
                        onEmailChange = loginViewModel::changeEmail,
                        onPasswordChange = loginViewModel::changePassword,
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(
                        onNavigateToLogin = {
                            registrationViewModel.cancel()
                            returnToLogin()
                        },
                        onRegisterClick = registrationViewModel::register,
                        isLoading = registrationState.isBusy,
                        error = registrationState.error,
                        email = registrationState.email,
                        onEmailChange = registrationViewModel::changeEmail,
                        retryAtMillis = registrationState.retryAtMillis,
                    )
                }
                composable(Routes.EMAIL_CONFIRMATION) {
                    BackHandler(enabled = !registrationState.isBusy) {
                        registrationViewModel.cancel()
                        returnToLogin()
                    }
                    EmailConfirmationScreen(
                        state = registrationState,
                        onEmailChange = registrationViewModel::changeEmail,
                        onCodeChange = registrationViewModel::changeCode,
                        onConfirm = registrationViewModel::confirmEmail,
                        onResend = registrationViewModel::resendCode,
                        onBackToLogin = {
                            registrationViewModel.cancel()
                            returnToLogin()
                        },
                    )
                }
            }
            composable(Routes.RECOVERY) {
                DisposableEffect(recoveryViewModel) {
                    onDispose { recoveryViewModel.clearSensitiveFields() }
                }
                fun leaveRecovery() {
                    if (recoveryState.isBusy) return
                    recoveryViewModel.leave()
                    if (!navController.popBackStack()) {
                        navController.navigate(
                            if (identity == null) Routes.LOGIN else Routes.PROFILE,
                        ) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                BackHandler { leaveRecovery() }
                PasswordRecoveryScreen(
                    state = recoveryState,
                    onEmailChange = recoveryViewModel::changeEmail,
                    onCodeChange = recoveryViewModel::changeCode,
                    onPasswordChange = recoveryViewModel::changePassword,
                    onConfirmationChange = recoveryViewModel::changeConfirmation,
                    onRequestCode = recoveryViewModel::requestCode,
                    onRecoverPassword = recoveryViewModel::recoverPassword,
                    onChangeEmail = recoveryViewModel::useAnotherEmail,
                    onBack = ::leaveRecovery,
                )
            }
            if (identity != null) {
                composable(Routes.PROFILE_GATE) {
                    ProfileGateScreen(
                        profileState,
                        { profileViewModel?.refresh() },
                        onLogout,
                        onRefreshIdentity,
                    )
                }
                composable(Routes.CATALOG) {
                    if (completed) CatalogScreen()
                    else
                        ProfileGateScreen(
                            profileState,
                            { profileViewModel?.refresh() },
                            onLogout,
                            onRefreshIdentity,
                        )
                }
                composable(Routes.PROFILE) {
                    if (!profileReady)
                        ProfileGateScreen(
                            profileState,
                            { profileViewModel?.refresh() },
                            onLogout,
                            onRefreshIdentity,
                        )
                    else
                        ProfileScreen(
                            onLogoutClick = onLogout,
                            accessEmail = identity.email,
                            isLoading = recoveryState.isBusy || isSessionBusy,
                            onRecoverPasswordClick = {
                                loginViewModel.clearError()
                                registrationViewModel.clearNotice()
                                recoveryViewModel.begin(identity.email)
                                navController.navigate(Routes.RECOVERY)
                            },
                            onRefreshIdentityClick = onRefreshIdentity,
                            state = profileState,
                            onRefreshProfileClick = { profileViewModel?.refresh() },
                            onEditProfileClick = {
                                openForm(
                                    if (completed) ProfileFormMode.EDIT
                                    else ProfileFormMode.ONBOARDING,
                                )
                            },
                            onManageAddressesClick = {
                                navController.navigate(Routes.ADDRESSES) { launchSingleTop = true }
                            },
                        )
                }
                for (route in listOf(Routes.ONBOARDING, Routes.EDIT_PROFILE)) {
                    composable(route) {
                        if (!profileReady)
                            ProfileGateScreen(
                                profileState,
                                { profileViewModel?.refresh() },
                                onLogout,
                                onRefreshIdentity,
                            )
                        else if (formViewModel != null) {
                            ProfileLifecycle(formViewModel::onVisible, formViewModel::onHidden)
                            fun leaveForm() {
                                if (formState.needsReload) profileViewModel?.refresh()
                                else
                                    formState.snapshot?.let {
                                        profileViewModel?.acceptServerProfile(it)
                                    }
                                formViewModel.close()
                                showRoot(Routes.PROFILE)
                            }
                            ProfileFormScreen(
                                state = formState,
                                onNameChange = formViewModel::changeName,
                                onPhoneChange = formViewModel::changePhone,
                                onEmailChange = formViewModel::changeEmail,
                                onSave = formViewModel::save,
                                onReload = formViewModel::reload,
                                onKeepDraft = formViewModel::keepDraft,
                                onUseServer = formViewModel::useServerValues,
                                onBack = ::leaveForm,
                                onContinue = {
                                    val saved = formState.snapshot
                                    if (
                                        formState.saved &&
                                            saved?.profile?.onboardingStatus ==
                                                OnboardingStatus.COMPLETED
                                    ) {
                                        profileViewModel?.acceptServerProfile(saved)
                                        val destination =
                                            if (formState.mode == ProfileFormMode.ONBOARDING)
                                                Routes.CATALOG
                                            else Routes.PROFILE
                                        formViewModel.close()
                                        showRoot(destination)
                                    }
                                },
                            )
                        }
                    }
                }
                composable(Routes.ADDRESSES) {
                    if (!profileReady)
                        ProfileGateScreen(
                            profileState,
                            { profileViewModel?.refresh() },
                            onLogout,
                            onRefreshIdentity,
                        )
                    else if (addressViewModel != null)
                        AddressListScreen(
                            addressState,
                            addressViewModel::beginCreate,
                            addressViewModel::beginEdit,
                            addressViewModel::makeDefault,
                            addressViewModel::prepareDelete,
                            addressViewModel::deleteConfirmed,
                            addressViewModel::cancelDelete,
                            addressViewModel::reload,
                            { showRoot(Routes.PROFILE) },
                        )
                }
                composable(Routes.ADDRESS_FORM) {
                    if (!profileReady)
                        ProfileGateScreen(
                            profileState,
                            { profileViewModel?.refresh() },
                            onLogout,
                            onRefreshIdentity,
                        )
                    else if (addressViewModel != null)
                        AddressFormScreen(
                            addressState,
                            addressViewModel::change,
                            addressViewModel::changeDefault,
                            addressViewModel::save,
                            addressViewModel::reload,
                            addressViewModel::keepDraft,
                            addressViewModel::useServerValues,
                            addressViewModel::returnToList,
                        )
                }
            }
        }
    }
}

@Composable
private fun ProfileLifecycle(onStart: () -> Unit, onStop: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val start = rememberUpdatedState(onStart)
    val stop = rememberUpdatedState(onStop)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start.value()
                Lifecycle.Event.ON_STOP -> stop.value()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start.value()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop.value()
        }
    }
}
