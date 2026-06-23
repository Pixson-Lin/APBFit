package com.pixson.apbfit.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixson.apbfit.R
import com.pixson.apbfit.data.model.ValidationResult
import com.pixson.apbfit.ui.util.runStatusLabelRes
import com.pixson.apbfit.ui.util.segmentStatusLabelRes
import com.pixson.apbfit.ui.util.validationResultLabelRes
import com.pixson.apbfit.ui.viewmodel.HistoryViewModel
import com.pixson.apbfit.ui.viewmodel.RunHistoryItem
import com.pixson.apbfit.ui.viewmodel.ValidationSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
                text = stringResource(R.string.nav_history),
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = {}) { Text("") }
        }

        if (uiState.accounts.isNotEmpty()) {
            HistoryAccountDropdown(
                accounts = uiState.accounts,
                selectedAccountEmail = uiState.selectedAccountEmail,
                onAccountSelected = viewModel::selectAccount,
            )
        }

        if (uiState.accounts.isEmpty()) {
            Text(
                text = stringResource(R.string.history_no_accounts),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 32.dp),
            )
        } else if (uiState.runs.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(uiState.runs, key = { it.id }) { run ->
                    RunHistoryCard(
                        run = run,
                        segments = if (run.isExpanded) uiState.expandedSegments else emptyList(),
                        onToggleExpand = { viewModel.toggleExpanded(run.id) },
                        onLogResult = { viewModel.openValidationSheet(run.id) },
                    )
                }
            }
        }

        uiState.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    uiState.validationSheet?.let { sheet ->
        ValidationBottomSheet(
            validationState = sheet,
            onDismiss = viewModel::dismissValidationSheet,
            onResultSelected = viewModel::setValidationResult,
            onStepCountChanged = viewModel::setValidationStepCount,
            onSave = viewModel::saveValidation,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryAccountDropdown(
    accounts: List<com.pixson.apbfit.ui.viewmodel.HistoryAccountOption>,
    selectedAccountEmail: String,
    onAccountSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        TextField(
            value = selectedAccountEmail,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.history_account_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .semantics {
                    contentDescription = context.getString(R.string.content_desc_history_account_dropdown)
                },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.email) },
                    onClick = {
                        onAccountSelected(account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RunHistoryCard(
    run: RunHistoryItem,
    segments: List<com.pixson.apbfit.ui.viewmodel.SegmentHistoryItem>,
    onToggleExpand: () -> Unit,
    onLogResult: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = run.startTimeLabel, style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.history_intensity, run.intensityLabel))
            Text(
                text = stringResource(
                    R.string.history_duration,
                    run.configuredDurationLabel,
                    run.actualDurationLabel,
                ),
            )
            Text(text = stringResource(R.string.history_steps, run.totalStepsWritten))
            Text(
                text = stringResource(
                    R.string.history_status,
                    stringResource(runStatusLabelRes(run.statusName)),
                ),
            )
            run.validationBadge?.let { badge ->
                Text(
                    text = stringResource(
                        R.string.history_validation_badge,
                        stringResource(validationResultLabelRes(badge)),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggleExpand) {
                    Text(
                        text = if (run.isExpanded) {
                            stringResource(R.string.history_collapse)
                        } else {
                            stringResource(R.string.history_expand)
                        },
                    )
                }
                TextButton(onClick = onLogResult) {
                    Text(
                        text = if (run.validationBadge == null) {
                            stringResource(R.string.history_log_result)
                        } else {
                            stringResource(R.string.history_edit_result)
                        },
                    )
                }
            }

            AnimatedVisibility(visible = run.isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    if (segments.isEmpty()) {
                        Text(
                            text = stringResource(R.string.history_no_segments),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        segments.forEach { segment ->
                            val statusLabel = stringResource(segmentStatusLabelRes(segment.writeStatus))
                            Text(
                                text = stringResource(
                                    R.string.history_segment_line,
                                    segment.segmentIndex,
                                    segment.timeRangeLabel,
                                    segment.steps,
                                    segment.distanceMeters,
                                    statusLabel,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            segment.errorMessage?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValidationBottomSheet(
    validationState: ValidationSheetState,
    onDismiss: () -> Unit,
    onResultSelected: (ValidationResult) -> Unit,
    onStepCountChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalSheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.validation_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = validationState.selectedResult == ValidationResult.ACCEPTED,
                    onClick = { onResultSelected(ValidationResult.ACCEPTED) },
                    label = { Text(stringResource(R.string.validation_accepted)) },
                )
                FilterChip(
                    selected = validationState.selectedResult == ValidationResult.REJECTED,
                    onClick = { onResultSelected(ValidationResult.REJECTED) },
                    label = { Text(stringResource(R.string.validation_rejected)) },
                )
            }
            OutlinedTextField(
                value = validationState.stepCountInput,
                onValueChange = onStepCountChanged,
                label = { Text(stringResource(R.string.validation_step_count_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.validation_save))
            }
        }
    }
}
