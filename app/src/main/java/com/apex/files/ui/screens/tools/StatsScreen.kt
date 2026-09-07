package com.apex.files.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.theme.ApexContainer
import com.apex.files.ui.theme.ApexContainerHigh

@Composable
fun StatsScreen() {
    val viewModel: StatsViewModel = apexViewModel("stats") { container -> StatsViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.scanPath()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analizando archivos...")
                }
            }
            state.error != null -> {
                Text(
                    "Error: ${state.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.stats != null -> {
                StatsContent(state.stats!!, state.scannedPath)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: StatsViewModel.FileStats, scannedPath: String?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Estadísticas de Archivos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            scannedPath?.let {
                Text(
                    "Ruta: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard("Archivos", stats.totalFiles.toString())
                StatCard("Carpetas", stats.totalDirs.toString())
                StatCard("Tamaño", SizeFormatter.format(stats.totalSize))
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text(
                "Por Categoría",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        stats.byCategory.forEach { (category, count) ->
            item {
                CategoryStatItem(category, count)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text(
                "Por Extensión",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        stats.byExtension.entries.take(10).forEach { (ext, count) ->
            item {
                ExtensionStatItem(ext, count)
            }
        }

        if (stats.largestFiles.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Text(
                    "Archivos más grandes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            stats.largestFiles.forEach { file ->
                item {
                    LargeFileItem(file)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        modifier = Modifier.width(100.dp),
        colors = CardDefaults.cardColors(containerColor = ApexContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryStatItem(category: Category, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            category.displayName,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ExtensionStatItem(ext: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            ".$ext",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun LargeFileItem(file: StatsViewModel.FileStatsItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ApexContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                SizeFormatter.format(file.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                file.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}