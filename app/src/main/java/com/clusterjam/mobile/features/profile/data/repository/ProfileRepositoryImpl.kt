package com.clusterjam.mobile.features.profile.data.repository

import com.clusterjam.mobile.core.network.SessionAccess
import com.clusterjam.mobile.features.profile.data.mapper.isProfileRevision
import com.clusterjam.mobile.features.profile.data.mapper.mapProfileError
import com.clusterjam.mobile.features.profile.data.mapper.toAddressBook
import com.clusterjam.mobile.features.profile.data.mapper.toAddressSnapshot
import com.clusterjam.mobile.features.profile.data.mapper.toProfileSnapshot
import com.clusterjam.mobile.features.profile.data.mapper.toRequest
import com.clusterjam.mobile.features.profile.data.remote.ProfileApiService
import com.clusterjam.mobile.features.profile.data.remote.dto.SetDefaultAddressRequestDto
import com.clusterjam.mobile.features.profile.data.remote.dto.UpdateProfileRequestDto
import com.clusterjam.mobile.features.profile.domain.model.AddressBookSnapshot
import com.clusterjam.mobile.features.profile.domain.model.AddressFields
import com.clusterjam.mobile.features.profile.domain.model.AddressSnapshot
import com.clusterjam.mobile.features.profile.domain.model.ProfileChanges
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure
import com.clusterjam.mobile.features.profile.domain.model.ProfileSnapshot
import com.clusterjam.mobile.features.profile.domain.repository.ProfileRepository
import com.clusterjam.mobile.features.profile.domain.validation.AddressValidator
import com.clusterjam.mobile.features.profile.domain.validation.ProfileValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepositoryImpl(
    private val apiService: ProfileApiService,
    private val sessionAccess: SessionAccess,
) : ProfileRepository {
    override suspend fun getMyAddresses(): Result<AddressBookSnapshot> = capture {
        sessionAccess.execute(apiService::getMyAddresses).getOrThrow().toAddressBook()
    }

    override suspend fun getMyAddress(id: String): Result<AddressSnapshot> = capture {
        requireAddressId(id)
        sessionAccess
            .execute { apiService.getMyAddress(it, id) }
            .getOrThrow()
            .toAddressSnapshot(200)
            .forId(id)
    }

    override suspend fun createMyAddress(
        fields: AddressFields,
        revision: String,
    ): Result<AddressSnapshot> = capture {
        requireRevision(revision)
        val request = addressRequest(fields)
        sessionAccess
            .execute { apiService.createMyAddress(it, revision, request) }
            .getOrThrow()
            .toAddressSnapshot(201)
    }

    override suspend fun updateMyAddress(
        id: String,
        fields: AddressFields,
        revision: String,
    ): Result<AddressSnapshot> = capture {
        requireAddressId(id)
        requireRevision(revision)
        val request = addressRequest(fields)
        sessionAccess
            .execute { apiService.updateMyAddress(it, id, revision, request) }
            .getOrThrow()
            .toAddressSnapshot(200)
            .forId(id)
    }

    override suspend fun setDefaultAddress(id: String, revision: String): Result<AddressSnapshot> =
        capture {
            requireAddressId(id)
            requireRevision(revision)
            sessionAccess
                .execute {
                    apiService.setDefaultAddress(it, id, revision, SetDefaultAddressRequestDto())
                }
                .getOrThrow()
                .toAddressSnapshot(200)
                .forId(id)
        }

    override suspend fun deleteMyAddress(id: String, revision: String): Result<Unit> = capture {
        requireAddressId(id)
        requireRevision(revision)
        val response =
            sessionAccess.execute { apiService.deleteMyAddress(it, id, revision) }.getOrThrow()
        if (response.code() != 204) throw ProfileFailure("INVALID_RESPONSE")
    }

    private fun requireAddressId(id: String) {
        if (!AddressValidator.isAddressId(id))
            throw ProfileFailure("ADDRESS_NOT_FOUND", status = 404)
    }

    private fun requireRevision(revision: String) {
        if (!isProfileRevision(revision))
            throw ProfileFailure("PRECONDITION_REQUIRED", status = 428)
    }

    private fun addressRequest(fields: AddressFields) =
        AddressValidator.normalize(fields).let {
            if (AddressValidator.invalidField(it) != null)
                throw ProfileFailure("ADDRESS_VALIDATION_ERROR")
            it.toRequest()
        }

    private fun AddressSnapshot.forId(id: String): AddressSnapshot {
        if (!address.id.equals(id, true)) throw ProfileFailure("INVALID_RESPONSE")
        return this
    }

    override suspend fun getMyProfile(): Result<ProfileSnapshot> = capture {
        sessionAccess.execute(apiService::getMyProfile).getOrThrow().toProfileSnapshot()
    }

    override suspend fun updateMyProfile(
        changes: ProfileChanges,
        revision: String,
    ): Result<ProfileSnapshot> = capture {
        if (!isProfileRevision(revision))
            throw ProfileFailure("PRECONDITION_REQUIRED", status = 428)
        val normalized =
            changes.copy(
                fullName = changes.fullName?.trim(),
                phone = changes.phone?.trim(),
                contactEmail = changes.contactEmail?.trim(),
            )
        ProfileValidator.changesError(normalized)?.let {
            throw ProfileFailure("VALIDATION_ERROR", validationError = it)
        }
        val request =
            UpdateProfileRequestDto(normalized.fullName, normalized.phone, normalized.contactEmail)
        sessionAccess
            .execute { authorization ->
                apiService.updateMyProfile(authorization, revision, request)
            }
            .getOrThrow()
            .toProfileSnapshot()
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(mapProfileError(error))
            }
        }
}
