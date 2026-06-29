package com.pixsonlin.apbfit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixsonlin.apbfit.service.RunServiceStarter
import com.pixsonlin.apbfit.service.RunSessionStateHolder
import com.pixsonlin.apbfit.service.RunSessionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ActiveRunViewModel @Inject constructor(
    runSessionStateHolder: RunSessionStateHolder,
    private val runServiceStarter: RunServiceStarter,
) : ViewModel() {
    private val tick = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    val uiState: StateFlow<RunSessionUiState> = combine(
        runSessionStateHolder.state,
        tick,
    ) { state, _ ->
        state.withCurrentTiming()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunSessionUiState())

    fun stopSession() {
        runServiceStarter.stopSession()
    }
}
