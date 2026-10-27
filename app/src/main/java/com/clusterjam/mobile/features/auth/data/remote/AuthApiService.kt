package com.clusterjam.mobile.features.auth.data.remote

import com.clusterjam.mobile.features.auth.data.remote.dto.ConfirmEmailRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.EmailConfirmationRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.EmailConfirmationRequestResponseDto
import com.clusterjam.mobile.features.auth.data.remote.dto.IdentityResponseDto
import com.clusterjam.mobile.features.auth.data.remote.dto.LoginRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.PasswordRecoveryRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RecoverPasswordRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RecoveryRequestResponseDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RefreshSessionRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RegistrationRequestDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RegistrationResponseDto
import com.clusterjam.mobile.features.auth.data.remote.dto.SessionResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {

    @POST("api/v1/registrations/email-confirmations")
    suspend fun confirmEmail(@Body request: ConfirmEmailRequestDto): Response<SessionResponseDto>

    @POST("api/v1/registrations/email-confirmation-requests")
    suspend fun requestEmailConfirmation(
        @Body request: EmailConfirmationRequestDto,
    ): Response<EmailConfirmationRequestResponseDto>

    @POST("api/v1/sessions")
    suspend fun login(
        @Body request: LoginRequestDto,
    ): SessionResponseDto

    @POST("api/v1/registrations")
    suspend fun register(
        @Body request: RegistrationRequestDto,
    ): RegistrationResponseDto

    @POST("api/v1/session-renewals")
    suspend fun renewSession(
        @Body request: RefreshSessionRequestDto,
    ): SessionResponseDto

    @GET("api/v1/sessions/current")
    suspend fun currentIdentity(
        @Header("Authorization") authorization: String,
    ): IdentityResponseDto

    @DELETE("api/v1/sessions/current")
    suspend fun logout(
        @Header("Authorization") authorization: String,
    ): Response<Unit>

    @POST("api/v1/password-recovery-requests")
    suspend fun requestPasswordRecovery(
        @Body request: PasswordRecoveryRequestDto,
    ): Response<RecoveryRequestResponseDto>

    @POST("api/v1/password-recoveries")
    suspend fun recoverPassword(
        @Body request: RecoverPasswordRequestDto,
    ): Response<Unit>
}
