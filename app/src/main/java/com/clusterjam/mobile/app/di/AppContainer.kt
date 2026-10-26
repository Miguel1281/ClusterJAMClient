package com.clusterjam.mobile.app.di

import android.content.Context
import com.clusterjam.mobile.core.network.HealthApiService
import com.clusterjam.mobile.core.network.NetworkModule
import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.local.SecureStorageImpl
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
import com.clusterjam.mobile.features.auth.data.repository.AuthRepositoryImpl
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val networkModule = NetworkModule()
    private val secureStorage: SecureStorage by lazy { SecureStorageImpl(applicationContext) }
    private val authApiService: AuthApiService by lazy {
        networkModule.retrofit.create(AuthApiService::class.java)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authApiService, secureStorage)
    }

    val healthApiService: HealthApiService by lazy {
        networkModule.retrofit.create(HealthApiService::class.java)
    }
}
