package com.clusterjam.mobile.features.profile.data.remote

import com.clusterjam.mobile.features.profile.data.remote.dto.AddressListResponseDto
import com.clusterjam.mobile.features.profile.data.remote.dto.AddressRequestDto
import com.clusterjam.mobile.features.profile.data.remote.dto.AddressResponseDto
import com.clusterjam.mobile.features.profile.data.remote.dto.ProfileResponseDto
import com.clusterjam.mobile.features.profile.data.remote.dto.SetDefaultAddressRequestDto
import com.clusterjam.mobile.features.profile.data.remote.dto.UpdateProfileRequestDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ProfileApiService {
    @GET("api/v1/profiles/me/addresses")
    fun getMyAddresses(@Header("Authorization") authorization: String): Call<AddressListResponseDto>

    @GET("api/v1/profiles/me/addresses/{addressId}")
    fun getMyAddress(
        @Header("Authorization") authorization: String,
        @Path("addressId") id: String,
    ): Call<AddressResponseDto>

    @POST("api/v1/profiles/me/addresses")
    fun createMyAddress(
        @Header("Authorization") authorization: String,
        @Header("If-Match") revision: String,
        @Body request: AddressRequestDto,
    ): Call<AddressResponseDto>

    @PATCH("api/v1/profiles/me/addresses/{addressId}")
    fun updateMyAddress(
        @Header("Authorization") authorization: String,
        @Path("addressId") id: String,
        @Header("If-Match") revision: String,
        @Body request: AddressRequestDto,
    ): Call<AddressResponseDto>

    @PATCH("api/v1/profiles/me/addresses/{addressId}")
    fun setDefaultAddress(
        @Header("Authorization") authorization: String,
        @Path("addressId") id: String,
        @Header("If-Match") revision: String,
        @Body request: SetDefaultAddressRequestDto,
    ): Call<AddressResponseDto>

    @DELETE("api/v1/profiles/me/addresses/{addressId}")
    fun deleteMyAddress(
        @Header("Authorization") authorization: String,
        @Path("addressId") id: String,
        @Header("If-Match") revision: String,
    ): Call<Unit>

    @GET("api/v1/profiles/me")
    fun getMyProfile(@Header("Authorization") authorization: String): Call<ProfileResponseDto>

    @PATCH("api/v1/profiles/me")
    fun updateMyProfile(
        @Header("Authorization") authorization: String,
        @Header("If-Match") revision: String,
        @Body request: UpdateProfileRequestDto,
    ): Call<ProfileResponseDto>
}
