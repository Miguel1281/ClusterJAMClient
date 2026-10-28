package com.clusterjam.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clusterjam.mobile.app.AppViewModel
import com.clusterjam.mobile.app.ClusterJamApp
import com.clusterjam.mobile.app.di.ViewModelFactories
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryViewModel
import com.clusterjam.mobile.features.auth.presentation.registration.RegistrationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
        )
        val container = (application as ClusterJamApplication).container
        val factory = ViewModelFactories(container.authRepository)
        setContent {
            val recoveryViewModel: PasswordRecoveryViewModel = viewModel(factory = factory)
            val appViewModel: AppViewModel = viewModel(factory = factory)
            val loginViewModel: LoginViewModel = viewModel(factory = factory)
            val registrationViewModel: RegistrationViewModel = viewModel(factory = factory)
            ClusterJamApp(appViewModel, recoveryViewModel, loginViewModel, registrationViewModel)
        }
    }
}
