package com.clusterjam.mobile.features.profile.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.ScreenHeading
import com.clusterjam.mobile.core.ui.theme.ClusterJAMClientTheme
import com.clusterjam.mobile.core.ui.theme.JamSpacing

@Composable
fun ProfileScreen(
    onLogoutClick: () -> Unit,
    accessEmail: String = "",
    isLoading: Boolean = false,
    onRecoverPasswordClick: () -> Unit = {},
    onRefreshIdentityClick: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = JamSpacing.FormWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(JamSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ScreenHeading("Mi perfil", "Tu cuenta, en un solo lugar.")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Cuenta",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Correo de acceso", style = MaterialTheme.typography.labelLarge)
                        Text(
                            accessEmail.ifBlank { "Sesión no disponible" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Se usa para iniciar sesión y recuperar el acceso.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRefreshIdentityClick, enabled = !isLoading) {
                            Text("Actualizar correo de acceso")
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Seguridad",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column {
                        ProfileAction(
                            "Cambiar contraseña",
                            R.drawable.ic_lock,
                            onRecoverPasswordClick,
                            !isLoading,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        ProfileAction(
                            if (isLoading) "Procesando…" else "Cerrar sesión",
                            R.drawable.ic_logout,
                            onLogoutClick,
                            !isLoading,
                        )
                    }
                }
                // Reserve space during requests, avoiding a jump in the actions below.
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Datos de contacto", style = MaterialTheme.typography.titleSmall)
                Text(
                    "La edición de datos y direcciones estará disponible próximamente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileAction(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(
    name = "Texto grande",
    showBackground = true,
    widthDp = 320,
    heightDp = 568,
    fontScale = 2f,
)
@Composable
fun ProfileScreenPreview() {
    ClusterJAMClientTheme { ProfileScreen({}, accessEmail = "cliente@ejemplo.com") }
}
