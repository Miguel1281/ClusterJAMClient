package com.clusterjam.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clusterjam.mobile.app.ClusterJamApp
import com.clusterjam.mobile.app.di.AuthViewModelFactory
import com.clusterjam.mobile.features.auth.presentation.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ClusterJamApplication).container
        val factory = AuthViewModelFactory(container.authRepository)
        setContent {
            val authViewModel: AuthViewModel = viewModel(factory = factory)
            ClusterJamApp(authViewModel)
        }
    }
}
