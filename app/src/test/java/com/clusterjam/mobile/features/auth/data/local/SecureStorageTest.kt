package com.clusterjam.mobile.features.auth.data.local

import android.content.SharedPreferences
import com.clusterjam.mobile.features.auth.domain.model.*
import java.io.IOException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class SecureStorageTest {
    @Test
    fun creationIsDeferredAndOpenReadSaveAndClearFailuresRemainStorageErrors() {
        var opens = 0
        val unopened = SecureStorageImpl {
            opens++
            throw IOException("Key unavailable")
        }
        assertEquals(0, opens)
        assertStorageFailure(StorageAction.OPEN) { unopened.getSession() }
        assertEquals(1, opens)
        val unreadable = SecureStorageImpl {
            preferences(mutableMapOf("access_token" to "access"), fail = "getString")
        }
        assertStorageFailure(StorageAction.READ) { unreadable.getSession() }
        val unwritable = SecureStorageImpl { preferences(fail = "commit") }
        assertStorageFailure(StorageAction.SAVE) {
            unwritable.saveSession(
                AuthSession(AuthIdentity("user", "client@example.com"), "access", "refresh", 1234),
            )
        }
        val uncleareable = SecureStorageImpl { preferences(fail = "clear") }
        assertStorageFailure(StorageAction.CLEAR) { uncleareable.clear() }
    }

    @Test
    fun partialPreferencesAreReportedWithoutBeingSilentlyErasedOrTreatedAsNoSession() {
        val values = mutableMapOf<String, Any>()
        val storage = SecureStorageImpl { preferences(values) }
        assertNull(storage.getSession())
        values["access_token"] = "partial-token"
        assertStorageFailure(StorageAction.READ) { storage.getSession() }
        assertEquals("partial-token", values["access_token"])
        storage.clear()
        assertNull(storage.getSession())
        val session =
            AuthSession(AuthIdentity("user", "client@example.com"), "access", "refresh", 1234)
        storage.saveSession(session)
        assertEquals(session, storage.getSession())
    }

    private fun assertStorageFailure(action: StorageAction, block: () -> Any?) {
        val failure = runCatching(block).exceptionOrNull() as AuthFailure
        assertEquals("STORAGE_ERROR", failure.code)
        assertEquals(action, failure.storageAction)
    }

    // A small in-memory preferences double: no Android keystore or real credentials in JVM tests.
    private fun preferences(
        values: MutableMap<String, Any> = mutableMapOf(),
        fail: String? = null,
    ): SharedPreferences {
        val editor =
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { proxy, method, args ->
                if (method.name == fail) {
                    if (fail == "commit") return@newProxyInstance false
                    throw IOException("Preferences operation failed")
                }
                when (method.name) {
                    "putString",
                    "putLong" -> {
                        values[args!![0] as String] = args[1]
                        proxy
                    }
                    "remove" -> {
                        values.remove(args!![0])
                        proxy
                    }
                    "clear" -> {
                        values.clear()
                        proxy
                    }
                    "commit" -> true
                    else -> error("Unexpected editor method: ${method.name}")
                }
            } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            if (method.name == fail) throw IOException("Preferences operation failed")
            when (method.name) {
                "contains" -> values.containsKey(args!![0])
                "getString" -> values[args!![0]] as String? ?: args[1]
                "getLong" -> values[args!![0]] as Long? ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences
    }
}
