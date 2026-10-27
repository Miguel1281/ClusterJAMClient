package com.clusterjam.mobile.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clusterjam.mobile.app.AppViewModel
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.presentation.AuthViewModel
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel

class ViewModelFactories(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val instance =
            when (modelClass) {
                AppViewModel::class.java -> AppViewModel(repository)
                LoginViewModel::class.java -> LoginViewModel(repository)
                AuthViewModel::class.java -> AuthViewModel(repository)
                else -> throw IllegalArgumentException("Unknown ViewModel class")
            }
        @Suppress("UNCHECKED_CAST")
        return instance as T
    }
}
