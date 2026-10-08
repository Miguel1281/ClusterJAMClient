package com.example.cluster_jam_client.data.auth

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

object NetworkModule {
    private const val BASE_URL = "http://192.168.1.241:8081/"

    private val loggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        Log.d("NetworkModule", "--> Enviando petición a: ${request.method()} ${request.url()}")
        val response = chain.proceed(request)
        Log.d("NetworkModule", "<-- Respuesta de ${request.url()}: HTTP ${response.code()}")
        response
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApiService: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }
}
