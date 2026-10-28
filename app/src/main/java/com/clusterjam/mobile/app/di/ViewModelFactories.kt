package com.clusterjam.mobile.app.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.clusterjam.mobile.app.AppViewModel
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryViewModel
import com.clusterjam.mobile.features.auth.presentation.registration.RegistrationViewModel

class ViewModelFactories(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass == PasswordRecoveryViewModel::class.java) {
            @Suppress("UNCHECKED_CAST")
            return PasswordRecoveryViewModel(repository, extras.createSavedStateHandle()) as T
        }
        if (modelClass == RegistrationViewModel::class.java) {
            @Suppress("UNCHECKED_CAST")
            return RegistrationViewModel(repository, extras.createSavedStateHandle()) as T
        }
        return create(modelClass)
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val instance =
            when (modelClass) {
                AppViewModel::class.java -> AppViewModel(repository)
                LoginViewModel::class.java -> LoginViewModel(repository)
                PasswordRecoveryViewModel::class.java -> PasswordRecoveryViewModel(repository)
                RegistrationViewModel::class.java ->
                    RegistrationViewModel(repository, SavedStateHandle())
                else -> throw IllegalArgumentException("Unknown ViewModel class")
            }
        @Suppress("UNCHECKED_CAST")
        return instance as T
    }
}
