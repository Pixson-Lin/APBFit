package com.pixsonlin.apbfit.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnabledAccountsPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEnabledAccountIds(): Set<String> {
        return prefs.getStringSet(KEY_ENABLED_ACCOUNT_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun setEnabledAccountIds(accountIds: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_ENABLED_ACCOUNT_IDS, accountIds.toSet())
            .apply()
    }

    fun enableAccount(accountId: String) {
        val updated = getEnabledAccountIds().toMutableSet()
        updated.add(accountId)
        setEnabledAccountIds(updated)
    }

    fun disableAccount(accountId: String) {
        val updated = getEnabledAccountIds().toMutableSet()
        updated.remove(accountId)
        setEnabledAccountIds(updated)
    }

    fun removeAccount(accountId: String) {
        disableAccount(accountId)
    }

    companion object {
        private const val PREFS_NAME = "apbfit_enabled_accounts_prefs"
        private const val KEY_ENABLED_ACCOUNT_IDS = "enabled_account_ids"
    }
}
