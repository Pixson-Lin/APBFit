package com.pixson.apbfit.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixson.apbfit.R
import com.pixson.apbfit.data.model.RunStatus
import com.pixson.apbfit.service.AccountRunUiState
import com.pixson.apbfit.ui.util.labelRes
import com.pixson.apbfit.ui.viewmodel.ActiveRunViewModel
import java.util.concurrent.TimeUnit

@Composable
fun ActiveRunScreen(
    viewModel: ActiveRunViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    val context = LocalContext.current
    val anyRunning = state.accounts.any { it.status == RunStatus.RUNNING }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_active_run),
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (session.sessionStatusLabel.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.active_run_session_status,
                            session.sessionStatusLabel,
                        ),
                    )
                }
                Text(text = stringResource(R.string.active_run_intensity, session.intensityName))
                Text(text = stringResource(R.string.active_run_elapsed, formatDuration(session.elapsedMillis)))
                Text(text = stringResource(R.string.active_run_remaining, formatDuration(session.remainingMillis)))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.accounts, key = { it.runId }) { account ->
                AccountRunCard(account)
            }
        }

        if (anyRunning) {
            Button(
                onClick = viewModel::stopSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = context.getString(R.string.content_desc_stop_run)
                    },
            ) {
                Text(stringResource(R.string.active_run_stop))
            }
        } else if (state.accounts.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.active_run_finished,
                    stringResource(RunStatus.COMPLETED.labelRes()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountRunCard(account: AccountRunUiState) {
    val statusLabel = stringResource(account.status.labelRes())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.active_run_account_line,
                    account.accountEmail,
                    account.totalSteps,
                    statusLabel,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = stringResource(R.string.active_run_segments, account.segmentsWritten))
            account.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%02d:%02d".format(minutes, seconds)
}
