package com.clusterjam.mobile.features.profile.data.remote

import com.clusterjam.mobile.features.profile.data.remote.dto.ProfileResponseDto
import com.clusterjam.mobile.features.profile.data.remote.dto.UpdateProfileRequestDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH

interface ProfileApiService {
    @GET("api/v1/profiles/me")
    fun getMyProfile(@Header("Authorization") authorization: String): Call<ProfileResponseDto>

    @PATCH("api/v1/profiles/me")
    fun updateMyProfile(
        @Header("Authorization") authorization: String,
        @Header("If-Match") revision: String,
        @Body request: UpdateProfileRequestDto,
    ): Call<ProfileResponseDto>
}
