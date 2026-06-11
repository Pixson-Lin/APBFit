package com.pixson.apbfit.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixson.apbfit.R

@Composable
fun ActiveRunScreen() {
    PlaceholderScreen(title = stringResource(R.string.nav_active_run))
}

@Composable
fun HistoryScreen() {
    PlaceholderScreen(title = stringResource(R.string.nav_history))
}

@Composable
fun SettingsScreen() {
    PlaceholderScreen(title = stringResource(R.string.nav_settings))
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
