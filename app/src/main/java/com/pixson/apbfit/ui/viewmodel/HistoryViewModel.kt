package com.pixson.apbfit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.db.entity.SegmentRecordEntity
import com.pixson.apbfit.data.model.IntensityLevel
import com.pixson.apbfit.data.model.ValidationResult
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.ui.util.RunFormatting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunHistoryItem(
    val id: String,
    val startTimeLabel: String,
    val intensityLabel: String,
    val configuredDurationLabel: String,
    val actualDurationLabel: String,
    val totalStepsWritten: Int,
    val statusLabel: String,
    val validationBadge: String?,
    val isExpanded: Boolean,
)

data class SegmentHistoryItem(
    val segmentIndex: Int,
    val timeRangeLabel: String,
    val steps: Int,
    val distanceMeters: Float,
    val statusLabel: String,
    val errorMessage: String?,
)

data class ValidationSheetState(
    val runId: String,
    val selectedResult: ValidationResult,
    val stepCountInput: String,
)

data class HistoryUiState(
    val runs: List<RunHistoryItem> = emptyList(),
    val expandedSegments: List<SegmentHistoryItem> = emptyList(),
    val validationSheet: ValidationSheetState? = null,
    val statusMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val runRepository: RunRepository,
) : ViewModel() {
    private val expandedRunId = MutableStateFlow<String?>(null)
    private val segments = MutableStateFlow<List<SegmentRecordEntity>>(emptyList())
    private val validationSheet = MutableStateFlow<ValidationSheetState?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private var segmentsJob: Job? = null

    private val runsFlow = accountRepository.activeAccount.flatMapLatest { account ->
        val accountId = account?.id
        if (accountId == null) {
            flowOf(emptyList())
        } else {
            runRepository.observeRuns(accountId)
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        runsFlow,
        expandedRunId,
        segments,
        validationSheet,
        statusMessage,
    ) { runs, expandedId, segmentEntities, sheet, status ->
        HistoryUiState(
            runs = runs.map { run ->
                val intensityName = runCatching {
                    IntensityLevel.valueOf(run.intensityLevel).displayName
                }.getOrDefault(run.intensityLevel)
                RunHistoryItem(
                    id = run.id,
                    startTimeLabel = RunFormatting.formatDateTime(run.startTime),
                    intensityLabel = intensityName,
                    configuredDurationLabel = RunFormatting.formatConfiguredDuration(run.durationMinutes),
                    actualDurationLabel = RunFormatting.formatActualDuration(run.startTime, run.endTime),
                    totalStepsWritten = run.totalStepsWritten,
                    statusLabel = run.status,
                    validationBadge = run.validationResult,
                    isExpanded = run.id == expandedId,
                )
            },
            expandedSegments = segmentEntities.map { segment ->
                SegmentHistoryItem(
                    segmentIndex = segment.segmentIndex,
                    timeRangeLabel = "${RunFormatting.formatTime(segment.startTime)} – ${RunFormatting.formatTime(segment.endTime)}",
                    steps = segment.steps,
                    distanceMeters = segment.distanceMeters,
                    statusLabel = if (segment.success) "SUCCESS" else "FAILED",
                    errorMessage = segment.errorMessage,
                )
            },
            validationSheet = sheet,
            statusMessage = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun toggleExpanded(runId: String) {
        if (expandedRunId.value == runId) {
            expandedRunId.value = null
            segments.value = emptyList()
            segmentsJob?.cancel()
            return
        }
        expandedRunId.value = runId
        segmentsJob?.cancel()
        segmentsJob = viewModelScope.launch {
            runRepository.observeSegments(runId).collect { segmentList ->
                segments.value = segmentList
            }
        }
    }

    fun openValidationSheet(runId: String) {
        viewModelScope.launch {
            val entity = runRepository.getRunById(runId) ?: return@launch
            validationSheet.value = ValidationSheetState(
                runId = runId,
                selectedResult = entity.validationResult?.let(ValidationResult::valueOf)
                    ?: ValidationResult.ACCEPTED,
                stepCountInput = entity.validationStepCount?.toString().orEmpty(),
            )
        }
    }

    fun dismissValidationSheet() {
        validationSheet.value = null
    }

    fun setValidationResult(result: ValidationResult) {
        validationSheet.value = validationSheet.value?.copy(selectedResult = result)
    }

    fun setValidationStepCount(input: String) {
        if (input.isEmpty() || input.all { it.isDigit() }) {
            validationSheet.value = validationSheet.value?.copy(stepCountInput = input)
        }
    }

    fun saveValidation() {
        val sheet = validationSheet.value ?: return
        viewModelScope.launch {
            val stepCount = sheet.stepCountInput.toIntOrNull()
            runRepository.logValidation(
                runId = sheet.runId,
                result = sheet.selectedResult,
                stepCount = stepCount,
                validationTime = System.currentTimeMillis(),
            )
            validationSheet.value = null
            statusMessage.value = "Validation saved."
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }
}
