package com.pixson.apbfit.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixson.apbfit.R
import com.pixson.apbfit.data.model.RunStatus
import com.pixson.apbfit.ui.viewmodel.ActiveRunViewModel
import java.util.concurrent.TimeUnit

@Composable
fun ActiveRunScreen(
    viewModel: ActiveRunViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_active_run),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = stringResource(R.string.active_run_intensity, state.intensityName))
        Text(text = stringResource(R.string.active_run_elapsed, formatDuration(state.elapsedMillis)))
        Text(text = stringResource(R.string.active_run_remaining, formatDuration(state.remainingMillis)))
        Text(text = stringResource(R.string.active_run_steps, state.totalSteps))
        Text(text = stringResource(R.string.active_run_segments, state.segmentsWritten))
        Text(text = stringResource(R.string.active_run_status, state.status.name))

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.status == RunStatus.RUNNING) {
            Button(
                onClick = viewModel::stopRun,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.active_run_stop))
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%02d:%02d".format(minutes, seconds)
}
