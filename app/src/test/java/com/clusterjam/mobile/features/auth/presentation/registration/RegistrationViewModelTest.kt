package com.clusterjam.mobile.features.auth.presentation.registration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.*
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel
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
class RegistrationViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        store.clear()
    }

    private fun <T : ViewModel> own(key: String, vm: T): T {
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>): V = vm as V
            }
        return ViewModelProvider(store, factory)[key, vm.javaClass]
    }

    @Test
    fun registrationRestoresOnlyEmailAndStageAndNeverAutomaticallyResends() = runTest {
        val repository = FakeAuthRepository()
        repository.registerAction = {
            Result.success(
                RegistrationResult(
                    AuthIdentity("id", it.email),
                    RegistrationStatus.PENDING_EMAIL_CONFIRMATION,
                ),
            )
        }
        var resends = 0
        repository.confirmationRequestAction = {
            resends++
            Result.success(EmailConfirmationRequestResult())
        }
        val saved = SavedStateHandle()
        val vm = own("registration", RegistrationViewModel(repository, saved))
        vm.register(" client@example.com ", "Password123!")
        runCurrent()
        assertEquals(RegistrationStep.WAITING_CODE, vm.uiState.value.step)
        vm.resendCode()
        runCurrent()
        assertEquals(R.string.auth_confirmation_requested, vm.uiState.value.notice?.resourceId)
        vm.changeCode("012345")
        assertEquals(
            setOf(RegistrationViewModel.EMAIL_KEY, RegistrationViewModel.STEP_KEY),
            saved.keys(),
        )
        val restored =
            own(
                "restored",
                RegistrationViewModel(
                    repository,
                    SavedStateHandle(
                        mapOf(
                            RegistrationViewModel.EMAIL_KEY to
                                saved.get<String>(RegistrationViewModel.EMAIL_KEY),
                            RegistrationViewModel.STEP_KEY to
                                saved.get<String>(RegistrationViewModel.STEP_KEY),
                        ),
                    ),
                ),
            )
        assertEquals("client@example.com", restored.uiState.value.email)
        assertEquals("", restored.uiState.value.code)
        assertEquals(RegistrationStep.WAITING_CODE, restored.uiState.value.step)
        assertEquals(1, resends)
        restored.changeEmail("another@example.com")
        assertEquals("", restored.uiState.value.code)
        restored.cancel()
        repository.registerAction = {
            Result.success(
                RegistrationResult(AuthIdentity("id", it.email), RegistrationStatus.ACTIVE),
            )
        }
        restored.register("another@example.com", "Password123!")
        runCurrent()
        assertEquals(RegistrationStep.COMPLETED, restored.uiState.value.step)
        assertEquals(R.string.auth_registered_active, restored.uiState.value.notice?.resourceId)
    }

    @Test
    fun confirmingPreventsDoubleSubmissionAndFinishesWhenIdentityNeedsRetry() = runTest {
        val repository = FakeAuthRepository()
        repository.states.value = SessionState.Unauthenticated
        val completion = CompletableDeferred<Unit>()
        var confirmations = 0
        var resends = 0
        repository.confirmationAction = { email, code ->
            confirmations++
            assertEquals("client@example.com", email)
            assertEquals("012345", code)
            completion.await()
            repository.states.value = SessionState.RecoverableError(AuthFailure("NETWORK_ERROR"))
            Result.success(Unit)
        }
        repository.confirmationRequestAction = {
            resends++
            Result.success(EmailConfirmationRequestResult())
        }
        val vm = own("registration", RegistrationViewModel(repository))
        vm.beginConfirmation("client@example.com")
        vm.changeCode("012345")
        vm.confirmEmail()
        vm.confirmEmail()
        vm.resendCode()
        assertEquals(1, confirmations)
        assertEquals(0, resends)
        completion.complete(Unit)
        runCurrent()
        assertEquals(RegistrationStep.COMPLETED, vm.uiState.value.step)
        assertEquals("", vm.uiState.value.code)
        vm.confirmEmail()
        assertEquals(1, confirmations)
    }

    @Test
    fun unconfirmedLoginPrefillsFlowAndRetryAfterPreventsEarlyResend() = runTest {
        val repository = FakeAuthRepository()
        repository.states.value = SessionState.Unauthenticated
        repository.loginAction = { Result.failure(AuthFailure("EMAIL_NOT_CONFIRMED")) }
        val login = own("login", LoginViewModel(repository))
        login.changeEmail(" client@example.com ")
        login.changePassword("Password123!")
        login.login()
        runCurrent()
        assertEquals("client@example.com", login.uiState.value.emailAwaitingConfirmation)
        var now = 1000L
        var requests = 0
        repository.confirmationRequestAction = {
            requests++
            if (requests == 1)
                Result.failure(AuthFailure("HTTP_429", status = 429, retryAfterMillis = 2000))
            else Result.success(EmailConfirmationRequestResult())
        }
        val vm = own("registration", RegistrationViewModel(repository, nowMillis = { now }))
        vm.beginConfirmation(requireNotNull(login.uiState.value.emailAwaitingConfirmation))
        vm.resendCode()
        runCurrent()
        assertEquals(RegistrationOperation.ERROR, vm.uiState.value.operation)
        assertEquals(3000L, vm.uiState.value.retryAtMillis)
        vm.resendCode()
        assertEquals(1, requests)
        now = 3000L
        vm.resendCode()
        runCurrent()
        assertEquals(2, requests)
        assertNull(vm.uiState.value.error)
    }
}
