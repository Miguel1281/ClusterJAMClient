package com.clusterjam.mobile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clusterjam.mobile.app.AppViewModel
import com.clusterjam.mobile.app.ClusterJamApp
import com.clusterjam.mobile.app.di.ViewModelFactories
import com.clusterjam.mobile.core.network.createRetrofit
import com.clusterjam.mobile.features.auth.data.local.SecureStorage
import com.clusterjam.mobile.features.auth.data.remote.AuthApiService
import com.clusterjam.mobile.features.auth.data.repository.AuthRepositoryImpl
import com.clusterjam.mobile.features.auth.domain.model.*
import com.clusterjam.mobile.features.auth.presentation.login.LoginViewModel
import com.clusterjam.mobile.features.auth.presentation.recovery.PasswordRecoveryViewModel
import com.clusterjam.mobile.features.auth.presentation.registration.RegistrationViewModel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerifiedCustomerNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepositoryImpl
    private lateinit var storage: MemoryStorage
    private lateinit var app: AppViewModel

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        storage = MemoryStorage()
        repository =
            AuthRepositoryImpl(
                createRetrofit(server.url("/").toString()).create(AuthApiService::class.java),
                storage,
                nowMillis = { NOW },
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun launch() {
        val factory = ViewModelFactories(repository)
        compose.setContent {
            val appViewModel: AppViewModel = viewModel(factory = factory)
            val loginViewModel: LoginViewModel = viewModel(factory = factory)
            val recoveryViewModel: PasswordRecoveryViewModel = viewModel(factory = factory)
            app = appViewModel
            val registrationViewModel: RegistrationViewModel = viewModel(factory = factory)
            ClusterJamApp(appViewModel, recoveryViewModel, loginViewModel, registrationViewModel)
        }
    }

    @Test
    fun administratorLoginCannotOpenCatalogOrReturnToPrivateScreensWithBack() {
        enqueue(200, sessionJson())
        enqueueIdentity("administrador")
        enqueue(204)
        launch()
        waitFor { repository.sessionState.value == SessionState.Unauthenticated }
        compose.onNodeWithText("Correo de acceso").performTextInput(EMAIL)
        compose.onNodeWithText("Contraseña", substring = false).performTextInput("Password123!")
        compose.onNodeWithText("Entrar", substring = false).performScrollTo().performClick()
        waitFor {
            repository.sessionState.value is SessionState.AccessDenied && !app.uiState.value.isBusy
        }
        compose.onNodeWithText(DENIAL).assertIsDisplayed()
        assertPrivateScreensAbsent()
        assertNull(storage.storedSession)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor { repository.sessionState.value == SessionState.Unauthenticated }
        compose.onNodeWithText("Iniciar sesión", substring = false).assertIsDisplayed()
        assertPrivateScreensAbsent()
        assertEquals(3, server.requestCount)
    }

    @Test
    fun administratorRestorationShowsDenialInsteadOfPrivateDestinations() {
        storage.storedSession = session()
        enqueueIdentity("administrador")
        enqueue(204)
        launch()
        waitFor {
            repository.sessionState.value is SessionState.AccessDenied && !app.uiState.value.isBusy
        }
        compose.onNodeWithText(DENIAL).assertIsDisplayed()
        assertPrivateScreensAbsent()
        compose.onNodeWithText("Usar otra cuenta").performClick()
        waitFor { repository.sessionState.value == SessionState.Unauthenticated }
        assertPrivateScreensAbsent()
    }

    @Test
    fun foregroundAdministratorRejectionDiscardsCatalogProfileBackStackEvenIfRemoteLogoutFails() {
        storage.storedSession = session()
        enqueueIdentity("cliente")
        launch()
        waitFor { repository.sessionState.value is SessionState.Authenticated }
        compose.onNodeWithText("Elige la prenda que vas a personalizar.").assertIsDisplayed()
        compose.onNodeWithText("Perfil", substring = false).performClick()
        compose.onNodeWithText("Mi perfil").assertIsDisplayed()
        enqueueIdentity("administrador")
        enqueue(503, """{"code":"SERVICE_UNAVAILABLE"}""")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitFor {
            repository.sessionState.value is SessionState.AccessDenied && !app.uiState.value.isBusy
        }
        compose.onNodeWithText(DENIAL).assertIsDisplayed()
        assertPrivateScreensAbsent()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor { repository.sessionState.value == SessionState.Unauthenticated }
        compose.onNodeWithText("Iniciar sesión", substring = false).assertIsDisplayed()
        assertPrivateScreensAbsent()
        assertNull(storage.storedSession)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun transientIdentityFailureKeepsPrivateDestinationsBlockedUntilRetryVerifiesClient() {
        storage.storedSession = session()
        enqueue(503, """{"code":"SERVICE_UNAVAILABLE"}""")
        launch()
        waitFor {
            repository.sessionState.value is SessionState.RecoverableError &&
                !app.uiState.value.isBusy
        }
        assertPrivateScreensAbsent()
        assertNotNull(storage.storedSession)
        enqueueIdentity("cliente")
        compose.onNodeWithText("Volver a intentar").performClick()
        waitFor { repository.sessionState.value is SessionState.Authenticated }
        compose.onNodeWithText("Elige la prenda que vas a personalizar.").assertIsDisplayed()
        assertEquals(2, server.requestCount)
    }

    private fun assertPrivateScreensAbsent() {
        compose.onNodeWithText("Mi perfil").assertDoesNotExist()
        compose.onNodeWithText("Elige la prenda que vas a personalizar.").assertDoesNotExist()
        compose.onNodeWithText("Catálogo", substring = false).assertDoesNotExist()
    }

    private fun waitFor(condition: () -> Boolean) {
        compose.waitUntil(15_000, condition)
        compose.waitForIdle()
    }

    private fun enqueueIdentity(role: String) {
        enqueue(200, """{"userId":"$USER_ID","email":"$EMAIL","appRole":"$role"}""")
    }

    private fun enqueue(status: Int, body: String = "") {
        server.enqueue(
            MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun session() =
        AuthSession(AuthIdentity(USER_ID, EMAIL), "access", "refresh", NOW + 3_600_000L)

    private fun sessionJson() =
        """{"userId":"$USER_ID","email":"$EMAIL","accessToken":"access","refreshToken":"refresh","expiresIn":3600,"tokenType":"bearer"}"""

    private class MemoryStorage : SecureStorage {
        @Volatile var storedSession: AuthSession? = null

        override fun getSession() = storedSession

        override fun saveSession(session: AuthSession) {
            this.storedSession = session
        }

        override fun clear() {
            storedSession = null
        }
    }

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
        private const val EMAIL = "client@example.com"
        private const val NOW = 1_700_000_000_000L
        private const val DENIAL =
            "Esta aplicación es exclusiva para cuentas de cliente. No tienes acceso desde este dispositivo"
    }
}
