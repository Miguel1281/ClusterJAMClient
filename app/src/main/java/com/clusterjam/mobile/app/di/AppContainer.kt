package com.clusterjam.mobile.app.di

import android.content.Context
import com.clusterjam.mobile.BuildConfig
import com.clusterjam.mobile.core.network.HealthApiService
import com.clusterjam.mobile.core.network.NetworkModule
import com.clusterjam.mobile.core.network.SessionAccess
import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.local.SecureStorageImpl
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
import com.clusterjam.mobile.features.auth.data.repository.AuthRepositoryImpl
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.profile.data.remote.ProfileApiService
import com.clusterjam.mobile.features.profile.data.repository.ProfileRepositoryImpl
import com.clusterjam.mobile.features.profile.domain.repository.ProfileRepository

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val networkModule = NetworkModule()
    private val secureStorage: SecureStorage by lazy { SecureStorageImpl(applicationContext) }
    private val authApiService: AuthApiService by lazy {
        networkModule.retrofit.create(AuthApiService::class.java)
    }

    private val sharedAuthRepository by lazy {
        AuthRepositoryImpl(
            authApiService,
            secureStorage,
            apiBaseUrl = BuildConfig.AUTH_BASE_URL,
            profileBaseUrl = BuildConfig.PROFILE_BASE_URL,
        )
    }

    val authRepository: AuthRepository
        get() = sharedAuthRepository

    val sessionAccess: SessionAccess
        get() = sharedAuthRepository

    private val profileApiService: ProfileApiService by lazy {
        networkModule.profileRetrofit.create(ProfileApiService::class.java)
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl(profileApiService, sessionAccess)
    }

    val healthApiService: HealthApiService by lazy {
        networkModule.retrofit.create(HealthApiService::class.java)
    }
}
