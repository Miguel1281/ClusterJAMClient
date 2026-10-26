package com.clusterjam.mobile.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute == Routes.CATALOG || currentRoute == Routes.PROFILE) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painterResource(android.R.drawable.ic_menu_agenda),
                                contentDescription = stringResource(R.string.nav_catalog)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_catalog)) },
                        selected = currentRoute == Routes.CATALOG,
                        onClick = onNavigateToCatalog
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painterResource(android.R.drawable.ic_menu_view),
                                contentDescription = stringResource(R.string.nav_cart)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_cart)) },
                        selected = false,
                        onClick = { /* Pending catalog integration. */ }
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painterResource(android.R.drawable.ic_menu_recent_history),
                                contentDescription = stringResource(R.string.nav_orders)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_orders)) },
                        selected = false,
                        onClick = { /* Pending orders integration. */ }
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painterResource(android.R.drawable.ic_menu_myplaces),
                                contentDescription = stringResource(R.string.nav_profile)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_profile)) },
                        selected = currentRoute == Routes.PROFILE,
                        onClick = onNavigateToProfile
                    )
                }
            }
        },
        content = content
    )
}
