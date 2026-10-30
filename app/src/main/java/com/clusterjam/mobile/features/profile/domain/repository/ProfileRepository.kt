package com.clusterjam.mobile.features.profile.domain.repository

import com.clusterjam.mobile.features.profile.domain.model.AddressBookSnapshot
import com.clusterjam.mobile.features.profile.domain.model.AddressFields
import com.clusterjam.mobile.features.profile.domain.model.AddressSnapshot
import com.clusterjam.mobile.features.profile.domain.model.ProfileChanges
import com.clusterjam.mobile.features.profile.domain.model.ProfileSnapshot

interface ProfileRepository {
    suspend fun getMyAddresses(): Result<AddressBookSnapshot>

    suspend fun getMyAddress(id: String): Result<AddressSnapshot>

    suspend fun createMyAddress(fields: AddressFields, revision: String): Result<AddressSnapshot>

    suspend fun updateMyAddress(
        id: String,
        fields: AddressFields,
        revision: String,
    ): Result<AddressSnapshot>

    suspend fun setDefaultAddress(id: String, revision: String): Result<AddressSnapshot>

    suspend fun deleteMyAddress(id: String, revision: String): Result<Unit>

    suspend fun getMyProfile(): Result<ProfileSnapshot>

    suspend fun updateMyProfile(
        changes: ProfileChanges,
        revision: String,
    ): Result<ProfileSnapshot>
}
