package com.clusterjam.mobile

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clusterjam.mobile.app.navigation.MainScaffold
import com.clusterjam.mobile.app.navigation.Routes
import com.clusterjam.mobile.core.ui.theme.ClusterJAMClientTheme
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.auth.presentation.login.*
import com.clusterjam.mobile.features.auth.presentation.recovery.*
import com.clusterjam.mobile.features.auth.presentation.registration.*
import com.clusterjam.mobile.features.catalog.presentation.CatalogScreen
import com.clusterjam.mobile.features.profile.presentation.ProfileScreen
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun viewport(
        width: Int = 360,
        height: Int = 640,
        scale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                ClusterJAMClientTheme {
                    Surface(Modifier.requiredSize(width.dp, height.dp)) { content() }
                }
            }
        }
    }

    @Test
    fun registrationActionIsVisibleWithoutScrollingOnCompactPhone() {
        viewport(width = 320, height = 520) { RegisterScreen({}, { _, _ -> }) }
        compose
            .onNode(hasText("Crear cuenta") and hasClickAction())
            .assertIsDisplayed()
            .assertIsNotEnabled()
        compose.onNodeWithText("Revisa tus datos").assertDoesNotExist()
        screenshot("register-320")
    }

    @Test
    fun registrationFitsNormalPhoneAndActionIsVisible() {
        viewport(width = 390, height = 740) { RegisterScreen({}, { _, _ -> }) }
        compose.onNodeWithText("Correo de acceso").assertIsDisplayed()
        compose.onNodeWithText("Confirmar contraseña").assertIsDisplayed()
        compose.onNode(hasText("Crear cuenta") and hasClickAction()).assertIsDisplayed()
        screenshot("register-390")
    }

    @Test
    fun largeTextFormCanReachAllFieldsWhileActionStaysVisible() {
        viewport(width = 320, height = 520, scale = 2f) { RegisterScreen({}, { _, _ -> }) }
        compose.onNode(hasText("Crear cuenta") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Confirmar contraseña").performScrollTo().assertIsDisplayed()
        screenshot("register-font-200")
    }

    @Test
    fun connectionErrorStaysAboveActionAfterScrollingAndDoesNotClearInput() {
        var error by mutableStateOf<AuthMessage?>(null)
        viewport(width = 320, height = 520) {
            var email by remember { mutableStateOf("cliente@ejemplo.com") }
            RegisterScreen(
                {},
                { _, _ -> },
                email = email,
                onEmailChange = { email = it },
                error = error,
            )
        }
        compose.onNodeWithText("Contraseña", substring = false).performTextInput("Password123!")
        compose
            .onNodeWithText("Confirmar contraseña")
            .performScrollTo()
            .performTextInput("Password123!")
        compose.runOnIdle { error = AuthMessage(R.string.auth_error_network) }
        val message =
            compose.onNodeWithText(compose.activity.getString(R.string.auth_error_network))
        val action = compose.onNode(hasText("Crear cuenta") and hasClickAction())
        message.assertIsDisplayed()
        action.assertIsDisplayed().assertIsEnabled()
        assertTrue(
            message.fetchSemanticsNode().boundsInRoot.bottom <=
                action.fetchSemanticsNode().boundsInRoot.top,
        )
        compose.onAllNodesWithContentDescription("Mostrar contraseña").assertCountEquals(2)
        screenshot("register-network-error")
    }

    @Test
    fun passwordToggleAndLoadingStateRemainAccessible() {
        var loading by mutableStateOf(false)
        viewport {
            LoginScreen(
                {},
                {},
                {},
                state =
                    LoginUiState(
                        email = "cliente@ejemplo.com",
                        password = "Password123!",
                        isLoading = loading,
                    ),
            )
        }
        compose.onNodeWithContentDescription("Mostrar contraseña").performClick()
        compose.onNodeWithContentDescription("Ocultar contraseña").assertIsDisplayed()
        compose.runOnIdle { loading = true }
        compose
            .onNode(hasText("Entrando…") and hasClickAction())
            .assertIsDisplayed()
            .assertIsNotEnabled()
        screenshot("login-loading")
    }

    @Test
    fun confirmationKeepsLeadingZeroAndDoesNotSendWithoutExplicitAction() {
        var requests = 0
        viewport {
            var state by remember {
                mutableStateOf(
                    RegistrationUiState(
                        email = "cliente@ejemplo.com",
                        step = RegistrationStep.WAITING_CODE,
                    ),
                )
            }
            EmailConfirmationScreen(
                state,
                { state = state.copy(email = it) },
                { state = state.copy(code = it) },
                { requests++ },
                { requests++ },
                {},
            )
        }
        compose.onNodeWithText("Código de seis dígitos").performTextInput("012345")
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("Confirmar y continuar").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("012345").assertExists()
        screenshot("confirmation")
    }

    @Test
    fun recoveryEmailValidationAppearsOnceBesideTheField() {
        viewport {
            PasswordRecoveryScreen(
                PasswordRecoveryUiState(
                    email = "invalido",
                    error = AuthMessage(R.string.auth_validation_email),
                ),
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
            )
        }
        compose
            .onAllNodesWithText(compose.activity.getString(R.string.auth_validation_email))
            .assertCountEquals(1)
        compose.onNodeWithText("Solicitar código").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun uncertainRecoveryExposesNewCodeActionAndErrorWithoutOldPasswordFields() {
        viewport(width = 320, height = 520) {
            PasswordRecoveryScreen(
                PasswordRecoveryUiState(
                    email = "cliente@ejemplo.com",
                    step = PasswordRecoveryStep.NEW_CODE_REQUIRED,
                    error = AuthMessage(R.string.auth_error_network),
                    notice = AuthMessage(R.string.auth_recovery_fresh_code),
                ),
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
            )
        }
        compose
            .onNodeWithText(compose.activity.getString(R.string.auth_error_network))
            .assertIsDisplayed()
        compose.onNodeWithText("Solicitar otro código").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Nueva contraseña").assertDoesNotExist()
        screenshot("recovery-new-code")
    }

    @Test
    fun keyboardKeepsFocusedFieldFeedbackAndPrimaryActionVisible() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge(
                statusBarStyle =
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ),
                navigationBarStyle =
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ),
            )
            compose.activity.window.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
        }
        var error by mutableStateOf<AuthMessage?>(null)
        compose.setContent {
            ClusterJAMClientTheme {
                Surface(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.systemBars.union(WindowInsets.displayCutout),
                        ),
                ) {
                    var email by remember { mutableStateOf("cliente@ejemplo.com") }
                    RegisterScreen(
                        {},
                        { _, _ -> },
                        email = email,
                        onEmailChange = { email = it },
                        error = error,
                    )
                }
            }
        }
        compose.onNodeWithText("Correo de acceso").performClick()
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithText("Correo de acceso").assertIsDisplayed()
        compose.onNode(hasText("Crear cuenta") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Correo de acceso").performImeAction()
        compose.onNodeWithText("Contraseña", substring = false).assertIsFocused()
        compose.onNodeWithText("Contraseña", substring = false).performImeAction()
        compose.onNodeWithText("Confirmar contraseña").assertIsFocused()
        compose.runOnIdle { error = AuthMessage(R.string.auth_error_network) }
        compose
            .onNodeWithText(compose.activity.getString(R.string.auth_error_network))
            .assertIsDisplayed()
        compose.onNode(hasText("Crear cuenta") and hasClickAction()).assertIsDisplayed()
        screenshot("register-keyboard-error")
        val fullScreen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "redesign/register-keyboard-full.png")
            .outputStream()
            .use {
                fullScreen.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
    }

    @Test
    fun successfulRegistrationNoticeIsVisibleAndNotDuplicated() {
        viewport { LoginScreen({}, {}, {}, notice = AuthMessage(R.string.auth_registered_active)) }
        compose
            .onAllNodesWithText(compose.activity.getString(R.string.auth_registered_active))
            .assertCountEquals(1)
        compose
            .onNodeWithText(compose.activity.getString(R.string.auth_registered_active))
            .assertIsDisplayed()
        screenshot("login-success")
    }

    @Test
    fun catalogMakesUnavailableSearchExplicit() {
        viewport {
            MainScaffold(Routes.CATALOG, {}, {}) { padding ->
                Box(Modifier.padding(padding)) { CatalogScreen() }
            }
        }
        compose.onNodeWithText("Buscar prendas").assertIsNotEnabled()
        compose.onNodeWithText("Búsqueda y filtros disponibles próximamente.").assertIsDisplayed()
        screenshot("catalog")
    }

    @Test
    fun profileOffersOnlyImplementedAccountActions() {
        viewport {
            MainScaffold(Routes.PROFILE, {}, {}) { padding ->
                Box(Modifier.padding(padding)) { ProfileScreen({}, "cliente@ejemplo.com") }
            }
        }
        compose.onNodeWithText("cliente@ejemplo.com").assertIsDisplayed()
        compose.onNodeWithText("Cambiar contraseña").assertHasClickAction()
        compose.onNodeWithText("Guardar cambios").assertDoesNotExist()
        screenshot("profile")
    }

    private fun screenshot(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "redesign").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
