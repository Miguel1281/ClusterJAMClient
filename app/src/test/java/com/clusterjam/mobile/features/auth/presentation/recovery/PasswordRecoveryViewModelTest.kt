package com.clusterjam.mobile.features.auth.presentation.recovery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.*
import com.clusterjam.mobile.testutils.FakeAuthRepository
import com.clusterjam.mobile.testutils.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordRecoveryViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        store.clear()
    }

    private fun own(key: String, vm: PasswordRecoveryViewModel): PasswordRecoveryViewModel {
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            }
        return ViewModelProvider(store, factory)[key, PasswordRecoveryViewModel::class.java]
    }

    @Test
    fun requestPreservesEmailAndRestoresNeitherSecretsNorAutomaticRequests() = runTest {
        val repository = FakeAuthRepository()
        val response = CompletableDeferred<Unit>()
        var requests = 0
        var updates = 0
        repository.recoveryRequestAction = {
            requests++
            assertEquals("client@example.com", it)
            response.await()
            Result.success(Unit)
        }
        repository.recoveryAction = { _, _, _ ->
            updates++
            Result.success(PasswordRecoveryResult())
        }
        val saved = SavedStateHandle()
        val vm = own("original", PasswordRecoveryViewModel(repository, saved))
        vm.begin(" client@example.com ")
        vm.requestCode()
        vm.requestCode()
        assertEquals(1, requests)
        assertTrue(vm.uiState.value.isBusy)
        response.complete(Unit)
        runCurrent()
        assertEquals(PasswordRecoveryStep.ENTER_CODE, vm.uiState.value.step)
        assertEquals(R.string.auth_recovery_requested, vm.uiState.value.notice?.resourceId)
        vm.changeCode("0x12345")
        vm.changePassword("Password123!")
        vm.changeConfirmation("different")
        vm.recoverPassword()
        assertEquals(0, updates)
        assertEquals(R.string.auth_password_mismatch, vm.uiState.value.error?.resourceId)
        assertEquals("012345", vm.uiState.value.code)
        assertEquals(
            setOf(PasswordRecoveryViewModel.EMAIL_KEY, PasswordRecoveryViewModel.STEP_KEY),
            saved.keys(),
        )
        val restored =
            own(
                "restored",
                PasswordRecoveryViewModel(
                    repository,
                    SavedStateHandle(
                        mapOf(
                            PasswordRecoveryViewModel.EMAIL_KEY to
                                saved.get<String>(PasswordRecoveryViewModel.EMAIL_KEY),
                            PasswordRecoveryViewModel.STEP_KEY to
                                saved.get<String>(PasswordRecoveryViewModel.STEP_KEY),
                        ),
                    ),
                ),
            )
        assertEquals("client@example.com", restored.uiState.value.email)
        assertEquals(PasswordRecoveryStep.ENTER_CODE, restored.uiState.value.step)
        assertSecretsCleared(restored)
        assertEquals(1, requests)
        restored.leave()
        restored.begin()
        assertEquals("client@example.com", restored.uiState.value.email)
        restored.changeEmail(" other@example.com ")
        assertEquals("other@example.com", restored.uiState.value.email)
        assertNull(restored.uiState.value.error)
        assertSecretsCleared(restored)
        restored.changeEmail("x".repeat(244) + "@example.com") // More than 254 characters.
        restored.requestCode()
        assertEquals(R.string.auth_validation_email, restored.uiState.value.error?.resourceId)
        assertEquals(1, requests)
        vm.leave()
        assertSecretsCleared(vm)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun updateFailuresRequireAnExplicitNewCodeAndNeverResubmitAutomatically() = runTest {
        val repository = FakeAuthRepository()
        var now = 1000L
        var requests = 0
        var updates = 0
        repository.recoveryRequestAction = {
            requests++
            Result.success(Unit)
        }
        val vm = own("recovery", PasswordRecoveryViewModel(repository, nowMillis = { now }))
        vm.begin("client@example.com")
        val errors =
            listOf(
                AuthFailure("INVALID_RECOVERY_CODE", status = 401) to
                    R.string.auth_error_recovery_code,
                AuthFailure("WEAK_PASSWORD", status = 422) to R.string.auth_error_weak_password,
                AuthFailure("SAME_PASSWORD", status = 409) to R.string.auth_error_same_password,
                AuthFailure("PASSWORD_RECOVERY_NOT_ALLOWED", status = 403) to
                    R.string.auth_error_recovery_not_allowed,
                AuthFailure("NETWORK_ERROR") to R.string.auth_error_network,
                AuthFailure("SERVICE_UNAVAILABLE", status = 503, retryAfterMillis = 2000) to
                    R.string.auth_error_unavailable,
            )
        for ((index, entry) in errors.withIndex()) {
            val (failure, message) = entry
            repository.recoveryAction = { _, _, _ ->
                updates++
                Result.failure(failure)
            }
            vm.requestCode()
            runCurrent()
            fill(vm)
            vm.recoverPassword()
            runCurrent()
            assertEquals(PasswordRecoveryStep.NEW_CODE_REQUIRED, vm.uiState.value.step)
            assertEquals(message, vm.uiState.value.error?.resourceId)
            assertEquals(R.string.auth_recovery_fresh_code, vm.uiState.value.notice?.resourceId)
            assertEquals("client@example.com", vm.uiState.value.email)
            assertSecretsCleared(vm)
            vm.recoverPassword()
            runCurrent()
            assertEquals(index + 1, updates)
            assertEquals(index + 1, requests)
        }
        vm.requestCode() // Retry-After still applies to an explicit resend.
        assertEquals(6, requests)
        now += 2000
        vm.requestCode()
        runCurrent()
        assertEquals(7, requests)
        assertEquals(PasswordRecoveryStep.ENTER_CODE, vm.uiState.value.step)
        assertNull(vm.uiState.value.error)
        vm.useAnotherEmail()
        assertEquals("client@example.com", vm.uiState.value.email)
        assertEquals(PasswordRecoveryStep.REQUEST_CODE, vm.uiState.value.step)
        assertSecretsCleared(vm)
    }

    @Test
    fun completedUpdateCannotRepeatEvenWhenLocalCleanupFailed() = runTest {
        for (cleanupFails in listOf(false, true)) {
            val repository = FakeAuthRepository()
            val completion = CompletableDeferred<PasswordRecoveryResult>()
            var updates = 0
            repository.recoveryAction = { email, code, password ->
                updates++
                assertEquals("client@example.com", email)
                assertEquals("012345", code)
                assertEquals("NewPassword123!", password)
                Result.success(completion.await())
            }
            val vm = own("cleanup-$cleanupFails", PasswordRecoveryViewModel(repository))
            vm.begin("client@example.com")
            vm.requestCode()
            runCurrent()
            fill(vm)
            vm.recoverPassword()
            vm.recoverPassword()
            vm.requestCode()
            assertEquals(1, updates)
            assertTrue(vm.uiState.value.isBusy)
            val failure =
                if (cleanupFails) AuthFailure("STORAGE_ERROR", storageAction = StorageAction.CLEAR)
                else null
            repository.states.value =
                if (failure != null) SessionState.AccessDenied(failure)
                else SessionState.Unauthenticated
            vm.clearSensitiveFields() // The global session transition can dispose the screen.
            completion.complete(PasswordRecoveryResult(failure))
            runCurrent()
            assertEquals(PasswordRecoveryStep.COMPLETED, vm.uiState.value.step)
            assertEquals(R.string.auth_password_updated, vm.uiState.value.notice?.resourceId)
            assertEquals(
                if (cleanupFails) R.string.auth_error_storage_clear else null,
                vm.uiState.value.cleanupFailure?.resourceId,
            )
            assertSecretsCleared(vm)
            repository.discardSession().getOrThrow()
            vm.recoverPassword()
            runCurrent()
            assertEquals(1, updates)
            assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
        }
    }

    private fun fill(vm: PasswordRecoveryViewModel) {
        vm.changeCode("012345")
        vm.changePassword("NewPassword123!")
        vm.changeConfirmation("NewPassword123!")
    }

    private fun assertSecretsCleared(vm: PasswordRecoveryViewModel) {
        assertEquals("", vm.uiState.value.code)
        assertEquals("", vm.uiState.value.password)
        assertEquals("", vm.uiState.value.confirmation)
    }
}
