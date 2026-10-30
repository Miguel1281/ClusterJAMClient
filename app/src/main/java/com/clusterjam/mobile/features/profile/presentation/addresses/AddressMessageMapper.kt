package com.clusterjam.mobile.features.profile.presentation.addresses

import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure
import com.clusterjam.mobile.features.profile.presentation.ProfileMessageMapper

object AddressMessageMapper {
    fun failure(error: Throwable): AuthMessage {
        val failure = error as? ProfileFailure
        return when {
            failure?.code == "ADDRESS_NOT_FOUND" -> AuthMessage(R.string.address_not_found)
            failure?.code in setOf("VALIDATION_ERROR", "ADDRESS_VALIDATION_ERROR") ||
                failure?.status == 422 -> AuthMessage(R.string.address_validation)
            failure?.status in setOf(412, 428) -> AuthMessage(R.string.address_conflict)
            else -> ProfileMessageMapper.failure(error)
        }
    }
}
