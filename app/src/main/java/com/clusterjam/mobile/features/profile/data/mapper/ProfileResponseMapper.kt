package com.clusterjam.mobile.features.profile.data.mapper

import com.clusterjam.mobile.features.profile.data.remote.dto.ProfileResponseDto
import com.clusterjam.mobile.features.profile.domain.model.CustomerProfile
import com.clusterjam.mobile.features.profile.domain.model.OnboardingStatus
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure
import com.clusterjam.mobile.features.profile.domain.model.ProfileSnapshot
import retrofit2.Response

internal fun isProfileRevision(value: String): Boolean = Regex("^\"[a-f0-9]{64}\"$").matches(value)

internal fun Response<ProfileResponseDto>.toProfileSnapshot(): ProfileSnapshot {
    if (code() != 200) invalidResponse()
    val dto = body() ?: invalidResponse()
    val revision = headers()["ETag"]?.takeIf(::isProfileRevision) ?: invalidResponse()
    val userId = dto.userId?.takeIf { UUID_PATTERN.matches(it) } ?: invalidResponse()
    val status =
        OnboardingStatus.values().find { it.name == dto.onboardingStatus } ?: invalidResponse()
    return ProfileSnapshot(
        CustomerProfile(
            userId = userId,
            fullName = dto.fullName,
            phone = dto.phone,
            contactEmail = dto.contactEmail,
            onboardingStatus = status,
            createdAt = dto.createdAt?.takeIf(String::isNotBlank) ?: invalidResponse(),
            updatedAt = dto.updatedAt?.takeIf(String::isNotBlank) ?: invalidResponse(),
        ),
        revision,
    )
}

private val UUID_PATTERN =
    Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")

private fun invalidResponse(): Nothing = throw ProfileFailure("INVALID_RESPONSE")
