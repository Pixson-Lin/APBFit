package com.pixsonlin.apbfit.ui.util

import android.content.Context
import androidx.annotation.StringRes
import com.pixsonlin.apbfit.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiStrings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun get(@StringRes resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)

    val runAlreadyActive: String get() = get(R.string.error_run_already_active)
    val recoveredRun: String get() = get(R.string.msg_recovered_run)
    val recoveredAfterRestart: String get() = get(R.string.error_recovered_after_restart)
    val signInFailed: String get() = get(R.string.error_sign_in_failed)
    val switchedAccount: String get() = get(R.string.msg_switched_account)
    val failedStartRun: String get() = get(R.string.error_failed_start_run)
    val cannotSwitchDuringRun: String get() = get(R.string.error_cannot_switch_during_run)
    val cannotSignOutDuringRun: String get() = get(R.string.error_cannot_sign_out_during_run)
    val signedOut: String get() = get(R.string.msg_signed_out)
    val historyCleared: String get() = get(R.string.msg_history_cleared)
    val validationSaved: String get() = get(R.string.msg_validation_saved)
    val fitPermissionsUpdated: String get() = get(R.string.msg_fit_permissions_updated)
    val fitPermissionsIncomplete: String get() = get(R.string.error_fit_permissions_incomplete)
    val fitPermissionCancelled: String get() = get(R.string.error_fit_permission_cancelled)
    val dataSourcesReady: String get() = get(R.string.msg_datasources_ready)
    val dataSourceSetupFailed: String get() = get(R.string.error_datasource_setup_failed)
    val writeFailed: String get() = get(R.string.error_write_failed)
    val debugRunStarted: String get() = get(R.string.msg_debug_run_started)
    val debugRequiresTwoAccounts: String get() = get(R.string.error_debug_requires_two_accounts)
    val unexpectedSuccess: String get() = get(R.string.msg_unexpected_success)
    val runNotFound: String get() = get(R.string.error_run_not_found)
    val accountNotAvailable: String get() = get(R.string.error_account_not_available)
    val zeroDuration: String get() = get(R.string.error_zero_duration)
    val unexpectedServiceError: String get() = get(R.string.error_unexpected_service)
    val injectedWriteFailure: String get() = get(R.string.error_injected_write_failure)
    val recoveredOrphanNone: String get() = get(R.string.msg_recovered_orphan_none)

    fun preflightFailed(accountEmail: String, detail: String?): String =
        get(R.string.error_preflight_account, accountEmail, detail ?: dataSourceSetupFailed)

    fun addedAccount(email: String): String = get(R.string.msg_added_account, email)
    fun testBatchWritten(steps: Int): String = get(R.string.msg_test_batch_written, steps)
    fun injectedFailure(message: String?): String =
        get(R.string.msg_injected_failure, message ?: writeFailed)
}
