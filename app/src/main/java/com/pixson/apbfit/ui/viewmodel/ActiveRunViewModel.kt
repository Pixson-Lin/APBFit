package com.pixson.apbfit.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.pixson.apbfit.service.RunServiceStarter
import com.pixson.apbfit.service.RunStateHolder
import com.pixson.apbfit.service.RunUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ActiveRunViewModel @Inject constructor(
    runStateHolder: RunStateHolder,
    private val runServiceStarter: RunServiceStarter,
) : ViewModel() {
    val uiState: StateFlow<RunUiState> = runStateHolder.state

    fun stopRun() {
        runServiceStarter.stopRun()
    }
}
