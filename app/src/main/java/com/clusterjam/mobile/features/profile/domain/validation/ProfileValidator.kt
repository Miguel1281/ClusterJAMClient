package com.clusterjam.mobile.features.profile.domain.validation

import com.clusterjam.mobile.features.auth.domain.validation.AuthValidator
import com.clusterjam.mobile.features.profile.domain.model.ProfileChanges

enum class ProfileValidationError {
    INVALID_NAME,
    INVALID_PHONE,
    INVALID_CONTACT_EMAIL,
    EMPTY_UPDATE,
}

object ProfileValidator {
    fun nameError(value: String): ProfileValidationError? {
        val text = value.trim()
        return ProfileValidationError.INVALID_NAME.takeIf {
            text.isBlank() ||
                text.codePointCount(0, text.length) > 120 ||
                text.any(Char::isISOControl)
        }
    }

    fun phoneError(value: String): ProfileValidationError? {
        val text = value.trim()
        return ProfileValidationError.INVALID_PHONE.takeIf {
            text.length > 30 ||
                !Regex("^\\+?[0-9 ().-]+$").matches(text) ||
                text.count { it in '0'..'9' } !in 7..15
        }
    }

    fun contactEmailError(value: String): ProfileValidationError? =
        ProfileValidationError.INVALID_CONTACT_EMAIL.takeIf {
            AuthValidator.emailError(value.trim()) != null || value.trim().any(Char::isISOControl)
        }

    fun changesError(changes: ProfileChanges): ProfileValidationError? {
        if (changes.fullName == null && changes.phone == null && changes.contactEmail == null)
            return ProfileValidationError.EMPTY_UPDATE
        return changes.fullName?.let(::nameError)
            ?: changes.phone?.let(::phoneError)
            ?: changes.contactEmail?.let(::contactEmailError)
    }
}
