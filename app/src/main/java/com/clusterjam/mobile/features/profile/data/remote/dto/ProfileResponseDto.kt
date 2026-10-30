package com.clusterjam.mobile.features.profile.data.remote.dto

data class ProfileResponseDto(
    val userId: String? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val contactEmail: String? = null,
    val onboardingStatus: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
