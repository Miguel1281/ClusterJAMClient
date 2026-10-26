package com.clusterjam.mobile.core.network

import retrofit2.Response
import retrofit2.http.GET

interface HealthApiService {
    @GET("health/live")
    suspend fun liveness(): HealthResponseDto

    @GET("health/ready")
    suspend fun readiness(): Response<HealthResponseDto>
}
