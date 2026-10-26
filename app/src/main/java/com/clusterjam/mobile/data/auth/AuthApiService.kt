package com.clusterjam.mobile.data.auth

import com.clusterjam.mobile.domain.auth.AuthCredentials
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {

    @POST("api/v1/sessions")
    suspend fun login(
        @Body credentials: AuthCredentials
    ): SessionResponse

    @POST("api/v1/registrations")
    suspend fun register(
        @Body credentials: AuthCredentials
    ): RegistrationResponse

    @POST("api/v1/session-renewals")
    suspend fun renewSession(
        @Body request: RefreshSessionRequest
    ): SessionResponse

    @GET("api/v1/sessions/current")
    suspend fun currentIdentity(
        @Header("Authorization") authorization: String
    ): IdentityResponse

    @DELETE("api/v1/sessions/current")
    suspend fun logout(
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @POST("api/v1/password-recovery-requests")
    suspend fun requestPasswordRecovery(
        @Body request: PasswordRecoveryRequest
    ): Response<RecoveryRequestResponse>

    @POST("api/v1/password-recoveries")
    suspend fun recoverPassword(
        @Body request: RecoverPasswordRequest
    ): Response<Unit>

    @GET("health/live")
    suspend fun liveness(): HealthResponse

    @GET("health/ready")
    suspend fun readiness(): Response<HealthResponse>
}

data class SessionResponse(
    val userId: String? = null,
    val email: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val tokenType: String? = null
)

data class IdentityResponse(
    val userId: String? = null,
    val email: String? = null
)

data class RegistrationResponse(
    val userId: String? = null,
    val email: String? = null,
    val status: String? = null
)

data class RefreshSessionRequest(
    val refreshToken: String
)

data class PasswordRecoveryRequest(
    val email: String
)

data class RecoverPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

data class RecoveryRequestResponse(
    val message: String? = null
)

data class HealthResponse(
    val status: String? = null
)
