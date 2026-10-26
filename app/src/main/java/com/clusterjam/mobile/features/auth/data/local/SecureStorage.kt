package com.clusterjam.mobile.features.auth.data.local

import com.clusterjam.mobile.features.auth.domain.model.AuthSession

interface SecureStorage {
    fun saveSession(session: AuthSession)
    fun getSession(): AuthSession?
    fun clear()
}
