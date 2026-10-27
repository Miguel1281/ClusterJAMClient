package com.clusterjam.mobile.app

import androidx.lifecycle.ViewModel
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
class SessionPresentationTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private val customer = VerifiedIdentity("client-id", "client@example.com", AppRole.CLIENT)

    @After
    fun tearDown() {
        store.clear()
    }

    private fun <T : ViewModel> own(key: String, vm: T): T {
        store.put(key, vm)
        return vm
    }

    @Test
    fun appKeepsTemporaryErrorVisibleUntilIdentityRetrySucceeds() = runTest {
        val repository = FakeAuthRepository()
        repository.restore = {
            val failure = AuthFailure("NETWORK_ERROR")
            repository.states.value = SessionState.RecoverableError(failure)
            Result.failure(failure)
        }
        val app = own("app", AppViewModel(repository))
        runCurrent()
        assertTrue(app.uiState.value.session is SessionState.RecoverableError)
        repository.restore = {
            repository.states.value = SessionState.Authenticated(customer)
            Result.success(customer)
        }
        app.retryIdentity()
        runCurrent()
        assertEquals(SessionState.Authenticated(customer), app.uiState.value.session)
    }

    @Test
    fun foregroundDoesNotKeepPreviousAccessDuringVerificationOrAfterDenial() = runTest {
        val repository = FakeAuthRepository()
        repository.restore = {
            repository.states.value = SessionState.Authenticated(customer)
            Result.success(customer)
        }
        val app = own("app", AppViewModel(repository))
        app.onForeground()
        val checked = CompletableDeferred<Unit>()
        repository.restore = {
            checked.await()
            val failure = AuthFailure("CLIENT_APP_ACCESS_DENIED")
            repository.states.value = SessionState.AccessDenied(failure)
            Result.failure(failure)
        }
        app.onBackground()
        assertEquals(SessionState.Checking, app.uiState.value.session)
        app.onForeground()
        runCurrent()
        assertEquals(SessionState.Checking, app.uiState.value.session)
        assertTrue(app.uiState.value.isBusy)
        checked.complete(Unit)
        runCurrent()
        assertTrue(app.uiState.value.session is SessionState.AccessDenied)
        assertFalse(app.uiState.value.isBusy)
    }

    @Test
    fun loginNormalizesEmailPreventsDoubleSubmissionAndKeepsErrorInFormState() = runTest {
        val repository = FakeAuthRepository()
        val app = own("app", AppViewModel(repository))
        val login = own("login", LoginViewModel(repository))
        val response = CompletableDeferred<Result<VerifiedIdentity>>()
        var calls = 0
        repository.loginAction = {
            calls++
            assertEquals("client@example.com", it.email)
            response.await()
        }
        login.changeEmail(" client@example.com ")
        login.changePassword("password123")
        login.login()
        login.login()
        assertEquals(1, calls)
        assertTrue(login.uiState.value.isLoading)
        assertEquals(SessionState.Unauthenticated, app.uiState.value.session)
        response.complete(Result.failure(AuthFailure("INVALID_CREDENTIALS")))
        runCurrent()
        assertFalse(login.uiState.value.isLoading)
        assertEquals(R.string.auth_error_credentials, login.uiState.value.error?.resourceId)
        assertEquals("", login.uiState.value.password)
        runCurrent()
        assertEquals(R.string.auth_error_credentials, login.uiState.value.error?.resourceId)
        login.changeEmail("another@example.com")
        assertNull(login.uiState.value.error)
    }

    @Test
    fun formResultCannotGrantAccessWithoutTheRepositoryVerifiedState() = runTest {
        val repository = FakeAuthRepository()
        val app = own("app", AppViewModel(repository))
        val login = own("login", LoginViewModel(repository))
        repository.loginAction = { Result.success(customer) }
        login.changeEmail(customer.email)
        login.changePassword("password123")
        login.login()
        runCurrent()
        assertEquals(SessionState.Unauthenticated, app.uiState.value.session)
    }

    @Test
    fun remoteLogoutFailureWithSuccessfulLocalClearStillLeavesTheAppSignedOut() = runTest {
        val repository = FakeAuthRepository()
        val pendingIdentity = CompletableDeferred<Unit>()
        repository.restore = {
            pendingIdentity.await()
            repository.states.value = SessionState.Authenticated(customer)
            Result.success(customer)
        }
        val app = own("app", AppViewModel(repository))
        repository.states.value = SessionState.Authenticated(customer)
        repository.logoutAction = {
            repository.states.value = SessionState.Unauthenticated
            Result.failure(AuthFailure("NETWORK_ERROR"))
        }
        app.logout()
        pendingIdentity.complete(Unit)
        runCurrent()
        assertEquals(SessionState.Unauthenticated, app.uiState.value.session)
        assertEquals(R.string.auth_logged_out_locally, app.uiState.value.notice?.resourceId)
        assertFalse(app.uiState.value.isBusy)
    }
}
