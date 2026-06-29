package com.pixsonlin.apbfit.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSourcePrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDataSourceIds(accountId: String): Triple<String?, String?, String?> {
        return Triple(
            prefs.getString(keySteps(accountId), null),
            prefs.getString(keyDistance(accountId), null),
            prefs.getString(keyActivity(accountId), null),
        )
    }

    fun saveDataSourceIds(
        accountId: String,
        steps: String,
        distance: String,
        activity: String,
    ) {
        prefs.edit()
            .putString(keySteps(accountId), steps)
            .putString(keyDistance(accountId), distance)
            .putString(keyActivity(accountId), activity)
            .apply()
    }

    fun clearForAccount(accountId: String) {
        prefs.edit()
            .remove(keySteps(accountId))
            .remove(keyDistance(accountId))
            .remove(keyActivity(accountId))
            .apply()
    }

    private fun keySteps(accountId: String) = "ds_steps_$accountId"
    private fun keyDistance(accountId: String) = "ds_distance_$accountId"
    private fun keyActivity(accountId: String) = "ds_activity_$accountId"

    companion object {
        private const val PREFS_NAME = "apbfit_datasource_prefs"
    }
}
