package com.pixsonlin.apbfit.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixsonlin.apbfit.data.repository.AccountRepository
import com.pixsonlin.apbfit.data.repository.RunRepository
import com.pixsonlin.apbfit.service.RunServiceStarter
import com.pixsonlin.apbfit.service.RunSessionStateHolder
import com.pixsonlin.apbfit.ui.util.UiStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val accountRepository: AccountRepository,
    val runSessionStateHolder: RunSessionStateHolder,
    private val runRepository: RunRepository,
    private val runServiceStarter: RunServiceStarter,
    private val uiStrings: UiStrings,
) : ViewModel() {
    init {
        viewModelScope.launch {
            accountRepository.initialize()
            runRepository.deleteOlderThan(
                System.currentTimeMillis() - RETENTION_MILLIS,
            )
            if (!runSessionStateHolder.isActive) {
                val sessionIds = runRepository.getOrphanSessionIds()
                sessionIds.forEach { sessionId ->
                    runServiceStarter.resumeOrphanSession(sessionId)
                }
                if (sessionIds.isNotEmpty()) {
                    Log.w(TAG, "Resuming ${sessionIds.size} orphan session(s) from cold start.")
                }
            }
        }
    }

    companion object {
        private const val TAG = "APBFit_Run"
        private const val RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}
