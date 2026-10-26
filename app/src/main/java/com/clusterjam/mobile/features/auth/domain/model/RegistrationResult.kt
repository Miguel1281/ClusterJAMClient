package com.clusterjam.mobile.features.auth.domain.model

enum class RegistrationStatus { PENDING_EMAIL_CONFIRMATION, ACTIVE }

data class RegistrationResult(val identity: AuthIdentity, val status: RegistrationStatus)
