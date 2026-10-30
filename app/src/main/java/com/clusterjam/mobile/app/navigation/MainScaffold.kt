package com.clusterjam.mobile.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clusterjam.mobile.R

@Composable
fun MainScaffold(
    currentRoute: String?,
    onNavigateToCatalog: () -> Unit,
    onNavigateToProfile: () -> Unit,
    catalogEnabled: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute == Routes.CATALOG || currentRoute == Routes.PROFILE) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        // Only implemented destinations are offered.
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    painterResource(R.drawable.ic_catalog),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(R.string.nav_catalog)) },
                            selected = currentRoute == Routes.CATALOG,
                            onClick = onNavigateToCatalog,
                            enabled = catalogEnabled,
                            colors = navigationColors(),
                        )
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    painterResource(R.drawable.ic_profile),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(R.string.nav_profile)) },
                            selected = currentRoute == Routes.PROFILE,
                            onClick = onNavigateToProfile,
                            colors = navigationColors(),
                        )
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
private fun navigationColors() =
    NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
