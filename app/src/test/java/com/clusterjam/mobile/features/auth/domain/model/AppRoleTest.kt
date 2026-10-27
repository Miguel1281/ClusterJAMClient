package com.clusterjam.mobile.features.auth.domain.model

import org.junit.Assert.*
import org.junit.Test

class AppRoleTest {
    @Test
    fun recognizesExactServerValuesWithoutDefaultingUnknownRoles() {
        assertEquals(AppRole.CLIENT, AppRole.fromContract("cliente"))
        assertEquals(AppRole.ADMINISTRATOR, AppRole.fromContract("administrador"))
        for (value in listOf(null, "", "client", "Cliente", "cliente ")) {
            assertNull(AppRole.fromContract(value))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun administratorIdentityCannotCreateAnAuthenticatedCustomerState() {
        SessionState.Authenticated(
            VerifiedIdentity("id", "admin@example.com", AppRole.ADMINISTRATOR),
        )
    }
}
