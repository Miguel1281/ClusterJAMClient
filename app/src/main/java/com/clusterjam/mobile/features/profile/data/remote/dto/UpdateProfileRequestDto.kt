package com.clusterjam.mobile.features.profile.data.remote.dto

// Gson omits null properties: absent PATCH fields preserve their server values.
data class UpdateProfileRequestDto(
    val fullName: String? = null,
    val phone: String? = null,
    val contactEmail: String? = null,
)
