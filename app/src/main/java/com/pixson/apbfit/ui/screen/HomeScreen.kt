package com.pixson.apbfit.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixson.apbfit.BuildConfig
import com.pixson.apbfit.R
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.domain.CheckStatus
import com.pixson.apbfit.domain.EnvironmentCheckId
import com.pixson.apbfit.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val addAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onAddAccountResult(result.data)
    }

    val fitnessPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onFitnessPermissionResult(result.data)
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshEnvironmentChecks()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshEnvironmentChecks()
    }

    // DIAGNOSTIC - TODO: remove before release
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshEnvironmentChecks()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEnvironmentChecks()
                // DIAGNOSTIC - TODO: remove before release
                if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACTIVITY_RECOGNITION,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            Text(
                text = stringResource(R.string.nav_home),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onNavigateToHistory) {
                    Text(stringResource(R.string.nav_history))
                }
                TextButton(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.nav_settings))
                }
            }
        }

        AccountSection(
            activeAccountEmail = uiState.activeAccountEmail,
            knownAccounts = uiState.knownAccounts,
            onSwitchAccount = viewModel::switchAccount,
            onAddAccount = { addAccountLauncher.launch(viewModel.addAccountIntent()) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        EnvironmentSection(
            checks = uiState.environmentChecks,
            onFixCheck = { checkId ->
                when (checkId) {
                    EnvironmentCheckId.BATTERY_OPTIMIZATION ->
                        settingsLauncher.launch(viewModel.batteryOptimizationIntent())
                    EnvironmentCheckId.GOOGLE_FIT_INSTALLED ->
                        settingsLauncher.launch(viewModel.googleFitIntent())
                    EnvironmentCheckId.FITNESS_PERMISSIONS ->
                        fitnessPermissionLauncher.launch(viewModel.getFitnessPermissionsIntent())
                    EnvironmentCheckId.NOTIFICATIONS -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            settingsLauncher.launch(viewModel.notificationSettingsIntent())
                        }
                    }
                }
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        RunConfigSection(
            selectedIntensity = uiState.selectedIntensity,
            durationMinutes = uiState.durationMinutes,
            batchSize = uiState.batchSize,
            onIntensitySelected = viewModel::setIntensity,
            onDurationChanged = viewModel::snapDurationFromSlider,
            onBatchSizeSelected = viewModel::setBatchSize,
        )

        Button(
            onClick = viewModel::startRun,
            enabled = uiState.canStartRun,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = context.getString(R.string.content_desc_start_run)
                },
        ) {
            Text(stringResource(R.string.start_run))
        }

        uiState.startBlockedReason?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (BuildConfig.DEBUG) {
            DebugPanel(
                isBusy = uiState.isBusy,
                onStartDebugRun = { viewModel.startDebugRun() },
                onStartForceFailRun = { viewModel.startDebugRun(forceFailNextWrite = true) },
                onEnsureDataSources = viewModel::ensureDataSources,
                onWriteTestBatch = viewModel::writeTestBatch,
                onTestInjectedFailure = viewModel::testInjectedFailure,
            )
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

@Composable
private fun AccountSection(
    activeAccountEmail: String?,
    knownAccounts: List<com.pixson.apbfit.ui.viewmodel.AccountSummary>,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
) {
    Text(
        text = stringResource(R.string.account_section_title),
        style = MaterialTheme.typography.titleMedium,
    )
    val accountLabel = activeAccountEmail ?: stringResource(R.string.active_account_none)
    Text(text = stringResource(R.string.active_account_label, accountLabel))
    knownAccounts.forEach { account ->
        OutlinedButton(
            onClick = { onSwitchAccount(account.id) },
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
        onClick = onAddAccount,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.add_google_account))
    }
}

@Composable
private fun EnvironmentSection(
    checks: List<com.pixson.apbfit.domain.EnvironmentCheck>,
    onFixCheck: (EnvironmentCheckId) -> Unit,
) {
    Text(
        text = stringResource(R.string.environment_section_title),
        style = MaterialTheme.typography.titleMedium,
    )
    val hasWarnings = checks.any { it.status == CheckStatus.WARN }
    Text(
        text = stringResource(
            if (hasWarnings) R.string.environment_warnings else R.string.environment_all_pass,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (hasWarnings) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    checks.forEach { check ->
        EnvironmentCheckRow(
            checkId = check.id,
            status = check.status,
            onFix = { onFixCheck(check.id) },
        )
    }
}

@Composable
private fun EnvironmentCheckRow(
    checkId: EnvironmentCheckId,
    status: CheckStatus,
    onFix: () -> Unit,
) {
    val label = when (checkId) {
        EnvironmentCheckId.BATTERY_OPTIMIZATION ->
            stringResource(R.string.check_battery_optimization)
        EnvironmentCheckId.GOOGLE_FIT_INSTALLED ->
            stringResource(R.string.check_google_fit_installed)
        EnvironmentCheckId.FITNESS_PERMISSIONS ->
            stringResource(R.string.check_fitness_permissions)
        EnvironmentCheckId.NOTIFICATIONS ->
            stringResource(R.string.check_notifications)
    }
    val statusLabel = if (status == CheckStatus.PASS) {
        stringResource(R.string.check_pass)
    } else {
        stringResource(R.string.check_warn)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (status == CheckStatus.PASS) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
            if (status == CheckStatus.WARN) {
                TextButton(onClick = onFix) {
                    Text(stringResource(R.string.check_fix))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunConfigSection(
    selectedIntensity: IntensityLevel,
    durationMinutes: Int,
    batchSize: Int,
    onIntensitySelected: (IntensityLevel) -> Unit,
    onDurationChanged: (Float) -> Unit,
    onBatchSizeSelected: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.run_config_section_title),
        style = MaterialTheme.typography.titleMedium,
    )

    Text(
        text = stringResource(R.string.intensity_label),
        style = MaterialTheme.typography.labelLarge,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IntensityLevel.entries.forEach { level ->
            FilterChip(
                selected = selectedIntensity == level,
                onClick = { onIntensitySelected(level) },
                label = { Text(level.displayName) },
            )
        }
    }
    Text(
        text = stringResource(
            R.string.intensity_metrics,
            selectedIntensity.cadenceSpm,
            selectedIntensity.strideMeters,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val durationLabel = formatDurationLabel(durationMinutes)
    Text(
        text = stringResource(R.string.duration_label, durationLabel),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    Slider(
        value = durationMinutes.toFloat(),
        onValueChange = onDurationChanged,
        valueRange = HomeViewModel.MIN_DURATION_MINUTES.toFloat()..HomeViewModel.MAX_DURATION_MINUTES.toFloat(),
        steps = ((HomeViewModel.MAX_DURATION_MINUTES - HomeViewModel.MIN_DURATION_MINUTES) /
            HomeViewModel.DURATION_STEP_MINUTES) - 1,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(R.string.batch_size_label),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (size in HomeViewModel.MIN_BATCH_SIZE..HomeViewModel.MAX_BATCH_SIZE) {
            FilterChip(
                selected = batchSize == size,
                onClick = { onBatchSizeSelected(size) },
                label = { Text(stringResource(R.string.batch_size_segments, size)) },
            )
        }
    }
}

@Composable
private fun DebugPanel(
    isBusy: Boolean,
    onStartDebugRun: () -> Unit,
    onStartForceFailRun: () -> Unit,
    onEnsureDataSources: () -> Unit,
    onWriteTestBatch: () -> Unit,
    onTestInjectedFailure: () -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        text = stringResource(R.string.debug_panel_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Button(
        onClick = onStartDebugRun,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.debug_start_run))
    }
    OutlinedButton(
        onClick = onStartForceFailRun,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.debug_start_run_force_fail))
    }
    Button(
        onClick = onEnsureDataSources,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.debug_ensure_datasources))
    }
    Button(
        onClick = onWriteTestBatch,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.debug_write_test_batch))
    }
    OutlinedButton(
        onClick = onTestInjectedFailure,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.debug_test_injected_failure))
    }
}

@Composable
private fun formatDurationLabel(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, mins)
    } else {
        stringResource(R.string.duration_minutes_only, minutes)
    }
}
