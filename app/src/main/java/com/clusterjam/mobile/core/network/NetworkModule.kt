package com.clusterjam.mobile.core.network

import android.util.Log
import com.clusterjam.mobile.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NetworkModule {
    val retrofit: Retrofit by lazy {
        createRetrofit(BuildConfig.AUTH_BASE_URL, logRequests = BuildConfig.DEBUG)
    }

    val profileRetrofit: Retrofit by lazy {
        createRetrofit(
            BuildConfig.PROFILE_BASE_URL,
            logRequests = BuildConfig.DEBUG,
            protectedRequest = { isProtectedProfileRequest(it, BuildConfig.PROFILE_BASE_URL) },
        )
    }
}

internal fun createRetrofit(
    baseUrl: String,
    logRequests: Boolean = false,
    protectedRequest: (Request) -> Boolean = { isProtectedApiRequest(it, baseUrl) },
): Retrofit {
    val clientBuilder =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addNetworkInterceptor { chain ->
                // Recheck each network exchange, including redirects within the same host.
                val request = chain.request()
                if (request.header("Authorization") != null && !protectedRequest(request)) {
                    throw java.io.IOException(
                        "Authorization is restricted to the configured protected API.",
                    )
                }
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                val original = chain.request()
                if (original.header("Authorization") != null && !protectedRequest(original)) {
                    throw java.io.IOException(
                        "Authorization is restricted to protected operations of the configured API.",
                    )
                }
                val body = original.body()
                val request =
                    if (original.method() in setOf("POST", "PATCH") && body != null) {
                        original
                            .newBuilder()
                            .method(
                                original.method(),
                                object : RequestBody() {
                                    override fun contentType() = body.contentType()

                                    override fun contentLength() = body.contentLength()

                                    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)

                                    override fun isOneShot() = true
                                },
                            )
                            .build()
                    } else original
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)

    if (logRequests) {
        clientBuilder.addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                val path = request.url().encodedPath()
                Log.d("NetworkModule", "--> ${request.method()} $path")
                val response = chain.proceed(request)
                Log.d("NetworkModule", "<-- $path: HTTP ${response.code()}")
                response
            },
        )
    }

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(clientBuilder.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
