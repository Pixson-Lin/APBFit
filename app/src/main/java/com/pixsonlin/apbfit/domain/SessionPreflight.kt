package com.pixsonlin.apbfit.domain

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pixsonlin.apbfit.domain.fit.FitWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPreflight @Inject constructor(
    private val fitWriter: FitWriter,
) {
    /**
     * Ensures Health Connect permissions are granted before a run starts.
     * All-or-nothing: returns failure on the first account that fails.
     */
    suspend fun ensureAll(accounts: List<GoogleSignInAccount>): Result<Unit> {
        if (accounts.isEmpty()) {
            return Result.failure(IllegalStateException("No accounts to preflight."))
        }
        for (account in accounts) {
            val ensureResult = fitWriter.ensureDataSources(account)
            if (ensureResult.isFailure) {
                return Result.failure(
                    PreflightException(
                        accountEmail = account.email.orEmpty(),
                        message = ensureResult.exceptionOrNull()?.message
                            ?: "Health Connect setup failed.",
                    ),
                )
            }
        }
        return Result.success(Unit)
    }
}

class PreflightException(
    val accountEmail: String,
    override val message: String,
) : Exception(message)
