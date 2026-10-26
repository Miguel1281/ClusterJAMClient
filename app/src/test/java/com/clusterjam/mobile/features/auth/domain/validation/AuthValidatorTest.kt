package com.clusterjam.mobile.features.auth.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {
    @Test
    fun emailMustHaveValidFormatAndAtMost254Characters() {
        assertNull(AuthValidator.emailError("client@example.com"))
        assertNull(AuthValidator.emailError("a".repeat(242) + "@example.com"))
        val invalidEmails = listOf(
            "", "no-at-sign", "client @example.com", "a".repeat(243) + "@example.com"
        )
        for (email in invalidEmails) {
            assertEquals(AuthValidationError.INVALID_EMAIL, AuthValidator.emailError(email))
        }
    }

    @Test
    fun passwordMustHaveAtLeastEightCharacters() {
        assertEquals(AuthValidationError.PASSWORD_TOO_SHORT, AuthValidator.passwordError("1234567"))
        assertNull(AuthValidator.passwordError("12345678"))
    }

    @Test
    fun codeKeepsLeadingZeroAndRequiresExactlySixAsciiDigits() {
        assertNull(AuthValidator.codeError("012345"))
        for (code in listOf("12345", "1234567", "12a456", " 12345", "１２３４５６")) {
            assertEquals(AuthValidationError.INVALID_CODE, AuthValidator.codeError(code))
        }
    }
}
