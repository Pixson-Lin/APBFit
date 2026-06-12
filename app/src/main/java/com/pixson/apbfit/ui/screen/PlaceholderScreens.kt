package com.pixson.apbfit.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixson.apbfit.R

@Composable
fun HistoryScreen(onNavigateBack: () -> Unit = {}) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_history),
        onNavigateBack = onNavigateBack,
    )
}

@Composable
fun SettingsScreen(onNavigateBack: () -> Unit = {}) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_settings),
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun PlaceholderScreen(
    title: String,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        TextButton(onClick = onNavigateBack) {
            Text(text = stringResource(R.string.nav_back))
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
