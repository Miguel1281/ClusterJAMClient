package com.clusterjam.mobile.features.auth.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import com.clusterjam.mobile.features.auth.domain.model.AuthIdentity
import com.clusterjam.mobile.features.auth.domain.model.AuthSession
import com.clusterjam.mobile.features.auth.domain.model.StorageAction

class SecureStorageImpl internal constructor(createPreferences: () -> SharedPreferences) :
    SecureStorage {
    constructor(
        context: Context,
    ) : this(
        {
            val application = context.applicationContext
            val key =
                MasterKey.Builder(application).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                application,
                "cluster_jam_secure_prefs",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        },
    )

    // Key creation/opening can fail. Defer both until an operation can report that failure.
    private val preferences by lazy { storageOperation(StorageAction.OPEN, createPreferences) }

    override fun saveSession(session: AuthSession) =
        storageOperation(StorageAction.SAVE) {
            val saved =
                preferences
                    .edit()
                    .remove("JWT_TOKEN")
                    .putString("access_token", session.accessToken)
                    .putString("refresh_token", session.refreshToken)
                    .putString("user_id", session.identity.userId)
                    .putString("email", session.identity.email)
                    .putLong("expires_at", session.expiresAtMillis)
                    .commit()
            if (!saved) throw AuthFailure("STORAGE_ERROR", storageAction = StorageAction.SAVE)
        }

    override fun getSession(): AuthSession? =
        storageOperation(StorageAction.READ) {
            val keys =
                listOf(
                    "access_token",
                    "refresh_token",
                    "user_id",
                    "email",
                    "expires_at",
                    "JWT_TOKEN",
                )
            if (keys.none(preferences::contains)) return@storageOperation null
            val access = preferences.getString("access_token", null)
            val refresh = preferences.getString("refresh_token", null)
            val userId = preferences.getString("user_id", null)
            val email = preferences.getString("email", null)
            val expiresAt = preferences.getLong("expires_at", 0L)
            if (
                access.isNullOrBlank() ||
                    access.any(Char::isWhitespace) ||
                    refresh.isNullOrBlank() ||
                    refresh.length > 4096 ||
                    refresh.any(Char::isWhitespace) ||
                    userId.isNullOrBlank() ||
                    email.isNullOrBlank() ||
                    expiresAt <= 0L
            ) {
                throw AuthFailure("STORAGE_ERROR", storageAction = StorageAction.READ)
            }
            AuthSession(AuthIdentity(userId, email), access, refresh, expiresAt)
        }

    override fun clear() =
        storageOperation(StorageAction.CLEAR) {
            if (!preferences.edit().clear().commit()) {
                throw AuthFailure("STORAGE_ERROR", storageAction = StorageAction.CLEAR)
            }
        }

    private fun <T> storageOperation(action: StorageAction, operation: () -> T): T =
        try {
            operation()
        } catch (failure: AuthFailure) {
            throw failure
        } catch (_: Exception) {
            throw AuthFailure("STORAGE_ERROR", storageAction = action)
        }
}
