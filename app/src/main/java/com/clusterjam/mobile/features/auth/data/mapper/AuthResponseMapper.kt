package com.clusterjam.mobile.features.auth.data.mapper

import com.clusterjam.mobile.features.auth.data.remote.dto.IdentityResponseDto
import com.clusterjam.mobile.features.auth.data.remote.dto.RegistrationResponseDto
import com.clusterjam.mobile.features.auth.data.remote.dto.SessionResponseDto
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.AuthIdentity
import com.clusterjam.mobile.features.auth.domain.model.AuthSession
import com.clusterjam.mobile.features.auth.domain.model.RegistrationResult
import com.clusterjam.mobile.features.auth.domain.model.RegistrationStatus

internal fun SessionResponseDto.toSession(nowMillis: Long): AuthSession {
    val access = accessToken?.takeIf { it.isNotBlank() && !it.any(Char::isWhitespace) }
        ?: invalidResponse()
    val refresh = refreshToken?.takeIf {
        it.isNotBlank() && it.length <= 4096 && !it.any(Char::isWhitespace)
    } ?: invalidResponse()
    val lifetime = expiresIn?.takeIf { it > 0 && it <= (Long.MAX_VALUE - nowMillis) / 1000 }
        ?: invalidResponse()
    if (!tokenType.equals("bearer", ignoreCase = true)) invalidResponse()
    return AuthSession(identity(userId, email), access, refresh, nowMillis + lifetime * 1000)
}

internal fun IdentityResponseDto.toIdentity(): AuthIdentity = identity(userId, email)

internal fun RegistrationResponseDto.toRegistration(): RegistrationResult {
    val registrationStatus = RegistrationStatus.values().find { it.name == status }
        ?: invalidResponse()
    return RegistrationResult(identity(userId, email), registrationStatus)
}

private fun identity(userId: String?, email: String?): AuthIdentity = AuthIdentity(
    userId?.takeIf { it.isNotBlank() } ?: invalidResponse(),
    email?.takeIf { it.isNotBlank() } ?: invalidResponse()
)

private fun invalidResponse(): Nothing = throw AuthFailure("INVALID_RESPONSE")
