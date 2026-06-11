package com.pixson.apbfit.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixson.apbfit.BuildConfig
import com.pixson.apbfit.R
import com.pixson.apbfit.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onAddAccountResult(result.data)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_home),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = stringResource(R.string.active_account_label, uiState.activeAccountEmail ?: "—"))
        Text(
            text = stringResource(
                R.string.fitness_permissions_label,
                if (uiState.hasFitnessPermissions) {
                    stringResource(R.string.check_pass)
                } else {
                    stringResource(R.string.check_warn)
                },
            ),
        )

        uiState.knownAccounts.forEach { account ->
            OutlinedButton(
                onClick = { viewModel.switchAccount(account.id) },
                enabled = !account.isActive,
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
            onClick = { addAccountLauncher.launch(viewModel.addAccountIntent()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_google_account))
        }

        if (BuildConfig.DEBUG) {
            Text(
                text = stringResource(R.string.debug_panel_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = viewModel::ensureDataSources,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.debug_ensure_datasources))
            }
            Button(
                onClick = viewModel::writeTestBatch,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.debug_write_test_batch))
            }
            OutlinedButton(
                onClick = viewModel::testInjectedFailure,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.debug_test_injected_failure))
            }
        }

        uiState.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
