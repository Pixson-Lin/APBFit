package com.pixson.apbfit.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryAccountPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedAccountId(): String? = prefs.getString(KEY_SELECTED_ACCOUNT_ID, null)

    fun setSelectedAccountId(accountId: String) {
        prefs.edit().putString(KEY_SELECTED_ACCOUNT_ID, accountId).apply()
    }

    fun clearSelectedAccountId() {
        prefs.edit().remove(KEY_SELECTED_ACCOUNT_ID).apply()
    }

    companion object {
        private const val PREFS_NAME = "apbfit_history_account_prefs"
        private const val KEY_SELECTED_ACCOUNT_ID = "selected_account_id"
    }
}
