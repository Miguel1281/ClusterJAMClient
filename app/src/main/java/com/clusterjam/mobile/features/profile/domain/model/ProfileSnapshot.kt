package com.clusterjam.mobile.features.profile.domain.model

/** The revision is the exact quoted server ETag, kept only in memory with its profile. */
data class ProfileSnapshot(val profile: CustomerProfile, val revision: String)
