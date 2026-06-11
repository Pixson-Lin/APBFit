package com.pixson.apbfit.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveAccountId(): String? = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)

    fun setActiveAccountId(accountId: String) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, accountId).apply()
    }

    fun getKnownAccountIds(): Set<String> =
        prefs.getStringSet(KEY_KNOWN_ACCOUNT_IDS, emptySet())?.toSet().orEmpty()

    fun addKnownAccountId(accountId: String) {
        val updated = getKnownAccountIds().toMutableSet()
        updated.add(accountId)
        prefs.edit().putStringSet(KEY_KNOWN_ACCOUNT_IDS, updated).apply()
    }

    fun removeKnownAccountId(accountId: String) {
        val updated = getKnownAccountIds().toMutableSet()
        updated.remove(accountId)
        prefs.edit().putStringSet(KEY_KNOWN_ACCOUNT_IDS, updated).apply()
    }

    fun clearActiveAccountId() {
        prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply()
    }

    companion object {
        private const val PREFS_NAME = "apbfit_account_prefs"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_KNOWN_ACCOUNT_IDS = "known_account_ids"
    }
}
