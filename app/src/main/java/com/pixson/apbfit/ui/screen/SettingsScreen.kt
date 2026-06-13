package com.pixson.apbfit.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixson.apbfit.R
import com.pixson.apbfit.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val addAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onAddAccountResult(result.data)
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onNavigateBack) {
                Text(stringResource(R.string.nav_back))
            }
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = {}) { Text("") }
        }

        Text(
            text = stringResource(R.string.settings_account_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = stringResource(R.string.active_account_label, uiState.activeAccountEmail ?: "—"))

        uiState.accounts.forEach { account ->
            OutlinedButton(
                onClick = { viewModel.switchAccount(account.id) },
                enabled = uiState.canSwitchAccount && !account.isActive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (account.isActive) {
                        stringResource(R.string.account_active, account.email)
                    } else {
                        account.email
                    },
                )
            }
        }

        Button(
            onClick = { addAccountLauncher.launch(viewModel.getSignInIntent()) },
            enabled = uiState.canSwitchAccount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_google_account))
        }

        OutlinedButton(
            onClick = viewModel::signOutCurrentAccount,
            enabled = uiState.canSwitchAccount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_sign_out))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_data_section),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(
            onClick = viewModel::requestClearHistoryConfirm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_clear_history))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_shortcuts_section),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(
            onClick = { settingsLauncher.launch(viewModel.batteryOptimizationIntent()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_battery_optimization))
        }
        OutlinedButton(
            onClick = { settingsLauncher.launch(viewModel.appDetailsIntent()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_app_details))
        }
        OutlinedButton(
            onClick = { settingsLauncher.launch(viewModel.notificationSettingsIntent()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_notifications))
        }
        OutlinedButton(
            onClick = { settingsLauncher.launch(viewModel.googleFitIntent()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_google_fit))
        }

        uiState.statusMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (uiState.showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearHistoryConfirm,
            title = { Text(stringResource(R.string.settings_clear_history_title)) },
            text = { Text(stringResource(R.string.settings_clear_history_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClearHistory) {
                    Text(stringResource(R.string.settings_clear_history_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearHistoryConfirm) {
                    Text(stringResource(R.string.settings_clear_history_cancel))
                }
            },
        )
    }
}
