package com.example.cluster_jam_client.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.cluster_jam_client.domain.auth.AuthIdentity
import com.example.cluster_jam_client.domain.auth.AuthSession
import com.example.cluster_jam_client.domain.auth.AuthFailure

interface SecureStorage {
    fun saveSession(session: AuthSession)
    fun getSession(): AuthSession?
    fun clear()
}

class SecureStorageImpl(context: Context) : SecureStorage {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "cluster_jam_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveSession(session: AuthSession) {
        // Both rotated tokens and their lifetime are persisted in one edit.
        val saved = preferences.edit()
            .remove("JWT_TOKEN")
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putString("user_id", session.identity.userId)
            .putString("email", session.identity.email)
            .putLong("expires_at", session.expiresAtMillis)
            .commit()
        if (!saved) throw AuthFailure("STORAGE_ERROR", "No se pudo guardar la sesión de forma segura.")
    }

    override fun getSession(): AuthSession? {
        val access = preferences.getString("access_token", null)
        val refresh = preferences.getString("refresh_token", null)
        val userId = preferences.getString("user_id", null)
        val email = preferences.getString("email", null)
        val expiresAt = preferences.getLong("expires_at", 0L)
        if (access.isNullOrBlank() || refresh.isNullOrBlank() || userId.isNullOrBlank() ||
            email.isNullOrBlank() || expiresAt <= 0L) {
            // The original access-token-only format cannot be renewed safely.
            if (preferences.contains("JWT_TOKEN") || access != null || refresh != null) clear()
            return null
        }
        return AuthSession(AuthIdentity(userId, email), access, refresh, expiresAt)
    }

    override fun clear() {
        if (!preferences.edit().clear().commit()) {
            throw AuthFailure("STORAGE_ERROR", "No se pudo borrar la sesión guardada. Vuelve a intentar.")
        }
    }
}
