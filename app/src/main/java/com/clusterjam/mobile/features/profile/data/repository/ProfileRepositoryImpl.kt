package com.clusterjam.mobile.features.profile.data.repository

import com.clusterjam.mobile.core.network.SessionAccess
import com.clusterjam.mobile.features.profile.data.mapper.isProfileRevision
import com.clusterjam.mobile.features.profile.data.mapper.mapProfileError
import com.clusterjam.mobile.features.profile.data.mapper.toProfileSnapshot
import com.clusterjam.mobile.features.profile.data.remote.ProfileApiService
import com.clusterjam.mobile.features.profile.data.remote.dto.UpdateProfileRequestDto
import com.clusterjam.mobile.features.profile.domain.model.ProfileChanges
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure
import com.clusterjam.mobile.features.profile.domain.model.ProfileSnapshot
import com.clusterjam.mobile.features.profile.domain.repository.ProfileRepository
import com.clusterjam.mobile.features.profile.domain.validation.ProfileValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepositoryImpl(
    private val apiService: ProfileApiService,
    private val sessionAccess: SessionAccess,
) : ProfileRepository {
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
