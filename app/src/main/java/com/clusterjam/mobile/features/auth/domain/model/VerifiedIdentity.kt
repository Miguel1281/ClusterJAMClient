package com.clusterjam.mobile.features.auth.domain.model

data class VerifiedIdentity(val userId: String, val email: String, val appRole: AppRole)
