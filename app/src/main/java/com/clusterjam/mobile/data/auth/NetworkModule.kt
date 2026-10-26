package com.clusterjam.mobile.data.auth

import android.util.Log
import com.clusterjam.mobile.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    val retrofit: Retrofit by lazy {
        createAuthRetrofit(BuildConfig.AUTH_BASE_URL, logRequests = BuildConfig.DEBUG)
    }

    val authApiService: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }
}

internal fun createAuthRetrofit(baseUrl: String, logRequests: Boolean = false): Retrofit {
    val clientBuilder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)

    if (logRequests) clientBuilder.addInterceptor(Interceptor { chain ->
        val request = chain.request()
        val path = request.url().encodedPath()
        Log.d("NetworkModule", "--> ${request.method()} $path")
        val response = chain.proceed(request)
        Log.d("NetworkModule", "<-- $path: HTTP ${response.code()}")
        response
    })

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(clientBuilder.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
