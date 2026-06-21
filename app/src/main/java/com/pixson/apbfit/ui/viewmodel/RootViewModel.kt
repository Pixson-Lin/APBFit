package com.pixson.apbfit.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.data.repository.RunRepository
import com.pixson.apbfit.service.RunSessionStateHolder
import com.pixson.apbfit.ui.util.UiStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val accountRepository: AccountRepository,
    val runSessionStateHolder: RunSessionStateHolder,
    private val runRepository: RunRepository,
    private val uiStrings: UiStrings,
) : ViewModel() {
    init {
        viewModelScope.launch {
            accountRepository.initialize()
            runRepository.deleteOlderThan(
                System.currentTimeMillis() - RETENTION_MILLIS,
            )
            val recovered = if (!runSessionStateHolder.isActive) {
                runRepository.recoverOrphanedSessions(uiStrings.recoveredAfterRestart)
            } else {
                0
            }
            if (recovered > 0) {
                runSessionStateHolder.clear()
                Log.w(TAG, "Recovered $recovered orphaned RUNNING run(s) from previous session.")
            }
        }
    }

    companion object {
        private const val TAG = "APBFit_Run"
        private const val RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}
