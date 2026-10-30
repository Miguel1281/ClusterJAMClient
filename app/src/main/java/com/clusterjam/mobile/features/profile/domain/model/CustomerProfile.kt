package com.clusterjam.mobile.features.profile.domain.model

data class CustomerProfile(
    val userId: String,
    val fullName: String?,
    val phone: String?,
    val contactEmail: String?,
    val onboardingStatus: OnboardingStatus,
    val createdAt: String,
    val updatedAt: String,
)
