package com.pixson.apbfit.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.pixson.apbfit.domain.CompactEnvironmentState
import com.pixson.apbfit.ui.viewmodel.EnabledAccountSummary
import com.pixson.apbfit.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEnvironmentChecks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.showAccountEditSheet) {
        AccountEditSheet(
            accounts = uiState.accountEditItems,
            enabledAccountCount = uiState.enabledAccounts.size,
            onDismiss = viewModel::dismissAccountEditSheet,
            onToggleEnabled = viewModel::setAccountEnabled,
            onSignOut = viewModel::signOutAccount,
            onAddAccount = {
                viewModel.launchAddAccount { intent -> addAccountLauncher.launch(intent) }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeTopBar(
            onNavigateToHistory = onNavigateToHistory,
            onNavigateToSettings = onNavigateToSettings,
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

        RunConfigSection(
            selectedIntensity = uiState.selectedIntensity,
            durationMinutes = uiState.durationMinutes,
            batchSize = uiState.batchSize,
            configLocked = uiState.isConfigLocked,
            onIntensitySelected = viewModel::setIntensity,
            onDurationChanged = viewModel::snapDurationFromSlider,
            onBatchChanged = viewModel::snapBatchFromSlider,
        )

        EnabledAccountsSection(
            enabledAccounts = uiState.enabledAccounts,
            configLocked = uiState.isConfigLocked,
            onEditAccounts = viewModel::openAccountEditSheet,
        )

        EnvironmentIconRow(
            icons = uiState.environmentIcons,
            onBatteryTap = {
                settingsLauncher.launch(viewModel.batteryOptimizationIntent())
            },
            onFitTap = {
                if (uiState.environmentIcons.fit == CheckStatus.WARN) {
                    viewModel.onFitIconTap(
                        launchSignIn = { intent -> fitnessPermissionLauncher.launch(intent) },
                        launchExternal = { intent -> settingsLauncher.launch(intent) },
                    )
                }
            },
            onNotificationsTap = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    settingsLauncher.launch(viewModel.notificationSettingsIntent())
                }
            },
            onAlarmsTap = {
                settingsLauncher.launch(viewModel.exactAlarmIntent())
            },
        )

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
private fun HomeTopBar(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunConfigSection(
    selectedIntensity: IntensityLevel,
    durationMinutes: Int,
    batchSize: Int,
    configLocked: Boolean,
    onIntensitySelected: (IntensityLevel) -> Unit,
    onDurationChanged: (Float) -> Unit,
    onBatchChanged: (Float) -> Unit,
) {
    val context = LocalContext.current
    var intensityExpanded by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.intensity_label),
        style = MaterialTheme.typography.labelLarge,
    )
    ExposedDropdownMenuBox(
        expanded = intensityExpanded,
        onExpandedChange = { if (!configLocked) intensityExpanded = it },
    ) {
        TextField(
            value = selectedIntensity.displayName,
            onValueChange = {},
            readOnly = true,
            enabled = !configLocked,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intensityExpanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .semantics {
                    contentDescription = context.getString(R.string.content_desc_intensity_dropdown)
                },
        )
        ExposedDropdownMenu(
            expanded = intensityExpanded,
            onDismissRequest = { intensityExpanded = false },
        ) {
            IntensityLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.displayName) },
                    onClick = {
                        onIntensitySelected(level)
                        intensityExpanded = false
                    },
                )
            }
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
        enabled = !configLocked,
        valueRange = HomeViewModel.MIN_DURATION_MINUTES.toFloat()..HomeViewModel.MAX_DURATION_MINUTES.toFloat(),
        steps = ((HomeViewModel.MAX_DURATION_MINUTES - HomeViewModel.MIN_DURATION_MINUTES) /
            HomeViewModel.DURATION_STEP_MINUTES) - 1,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(R.string.batch_size_label_value, batchSize),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    Slider(
        value = batchSize.toFloat(),
        onValueChange = onBatchChanged,
        enabled = !configLocked,
        valueRange = HomeViewModel.MIN_BATCH_SIZE.toFloat()..HomeViewModel.MAX_BATCH_SIZE.toFloat(),
        steps = HomeViewModel.MAX_BATCH_SIZE - HomeViewModel.MIN_BATCH_SIZE - 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EnabledAccountsSection(
    enabledAccounts: List<EnabledAccountSummary>,
    configLocked: Boolean,
    onEditAccounts: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.account_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(
            onClick = onEditAccounts,
            enabled = !configLocked,
            modifier = Modifier.semantics {
                contentDescription = context.getString(R.string.content_desc_account_edit)
            },
        ) {
            Text(stringResource(R.string.account_edit_button))
        }
    }
    if (enabledAccounts.isEmpty()) {
        Text(
            text = stringResource(R.string.enabled_accounts_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        enabledAccounts.forEach { account ->
            Text(
                text = account.email,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EnvironmentIconRow(
    icons: CompactEnvironmentState,
    onBatteryTap: () -> Unit,
    onFitTap: () -> Unit,
    onNotificationsTap: () -> Unit,
    onAlarmsTap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        EnvironmentIcon(
            label = stringResource(R.string.env_icon_battery),
            status = icons.battery,
            onClick = onBatteryTap,
        )
        EnvironmentIcon(
            label = stringResource(R.string.env_icon_fit),
            status = icons.fit,
            onClick = onFitTap,
        )
        EnvironmentIcon(
            label = stringResource(R.string.env_icon_notifications),
            status = icons.notifications,
            onClick = onNotificationsTap,
        )
        EnvironmentIcon(
            label = stringResource(R.string.env_icon_alarms),
            status = icons.alarms,
            onClick = onAlarmsTap,
        )
    }
}

@Composable
private fun EnvironmentIcon(
    label: String,
    status: CheckStatus,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val indicatorColor = if (status == CheckStatus.PASS) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val statusLabel = stringResource(
        if (status == CheckStatus.PASS) R.string.env_status_pass else R.string.env_status_warn,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics {
                contentDescription = context.getString(
                    R.string.content_desc_env_icon,
                    label,
                    statusLabel,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(indicatorColor),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
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
