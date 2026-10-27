package com.clusterjam.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clusterjam.mobile.app.AppViewModel
import com.clusterjam.mobile.app.ClusterJamApp
import com.clusterjam.mobile.app.di.ViewModelFactories
import com.clusterjam.mobile.features.auth.presentation.AuthViewModel
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ClusterJamApplication).container
        val factory = ViewModelFactories(container.authRepository)
        setContent {
            val authViewModel: AuthViewModel = viewModel(factory = factory)
            val appViewModel: AppViewModel = viewModel(factory = factory)
            val loginViewModel: LoginViewModel = viewModel(factory = factory)
            ClusterJamApp(appViewModel, authViewModel, loginViewModel)
        }
    }
}
