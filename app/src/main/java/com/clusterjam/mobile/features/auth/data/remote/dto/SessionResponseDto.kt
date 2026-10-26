package com.clusterjam.mobile.features.auth.data.remote.dto

data class SessionResponseDto(
    val userId: String? = null,
    val email: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val tokenType: String? = null
)
