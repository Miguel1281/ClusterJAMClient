package com.clusterjam.mobile.features.auth.domain.model

data class AuthSession(
    val identity: AuthIdentity,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
) {
    fun needsRenewal(nowMillis: Long): Boolean = expiresAtMillis <= nowMillis + 30_000L
}
