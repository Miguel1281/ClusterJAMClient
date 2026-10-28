package com.clusterjam.mobile.features.catalog.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.ScreenHeading
import com.clusterjam.mobile.core.ui.theme.ClusterJAMClientTheme
import com.clusterjam.mobile.core.ui.theme.JamSpacing

@Composable
fun CatalogScreen() {
    val products =
        listOf(
            ProductItem("Manga corta", "$220 MXN"),
            ProductItem("Manga larga", "$260 MXN"),
            ProductItem("Sudadera", "$450 MXN"),
            ProductItem("Manga corta clásica", "$220 MXN"),
        )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns =
                GridCells.Adaptive(if (LocalDensity.current.fontScale >= 1.5f) 240.dp else 144.dp),
            modifier = Modifier.widthIn(max = JamSpacing.ContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(JamSpacing.Page),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ScreenHeading("Catálogo", "Elige la prenda que vas a personalizar.")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Catalog integration is pending. Do not imply that search or filters work.
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        enabled = false,
                        singleLine = true,
                        label = { Text("Buscar prendas") },
                        placeholder = { Text("Próximamente") },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                disabledContainerColor = MaterialTheme.colorScheme.surface,
                                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledPlaceholderColor =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLeadingIconColor =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                    Text(
                        "Búsqueda y filtros disponibles próximamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Todos", "Manga corta", "Manga larga", "Sudadera").forEach { category
                            ->
                            FilterChip(
                                selected = category == "Todos",
                                onClick = {},
                                enabled = false,
                                label = { Text(category) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        disabledLabelColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledSelectedContainerColor =
                                            MaterialTheme.colorScheme.primaryContainer,
                                    ),
                            )
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Prendas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Vista previa",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(products, key = { it.name }) { ProductCard(it) }
        }
    }
}

data class ProductItem(val name: String, val price: String)

@Composable
fun ProductCard(product: ProductItem) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.05f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_tshirt),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Desde",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(product.price, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Tablet", showBackground = true, widthDp = 800, heightDp = 1000)
@Preview(
    name = "Texto grande",
    showBackground = true,
    widthDp = 320,
    heightDp = 568,
    fontScale = 2f,
)
@Composable
fun CatalogScreenPreview() {
    ClusterJAMClientTheme { CatalogScreen() }
}
