package com.clusterjam.mobile.features.profile.domain.repository

import com.clusterjam.mobile.features.profile.domain.model.ProfileChanges
import com.clusterjam.mobile.features.profile.domain.model.ProfileSnapshot

interface ProfileRepository {
    suspend fun getMyProfile(): Result<ProfileSnapshot>

    suspend fun updateMyProfile(
        changes: ProfileChanges,
        revision: String,
    ): Result<ProfileSnapshot>
}
