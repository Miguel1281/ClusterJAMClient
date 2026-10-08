package com.example.cluster_jam_client.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cluster_jam_client.data.auth.AuthRepositoryImpl
import com.example.cluster_jam_client.data.auth.NetworkModule
import com.example.cluster_jam_client.data.auth.SecureStorageImpl

class AuthViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            val repository = AuthRepositoryImpl(NetworkModule.authApiService, SecureStorageImpl(applicationContext))
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
