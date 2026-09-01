package com.pixsonlin.apbfit.domain

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.pixsonlin.apbfit.BuildConfig
import com.pixsonlin.apbfit.data.repository.AccountRepository
import com.pixsonlin.apbfit.domain.fit.FitWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPreflight @Inject constructor(
    private val accountRepository: AccountRepository,
    private val fitWriter: FitWriter,
) {
    /**
     * Ensures fitness permissions and DataSources for every account.
     * All-or-nothing: returns failure on the first account that fails.
     */
    suspend fun ensureAll(accounts: List<GoogleSignInAccount>): Result<Unit> {
        if (accounts.isEmpty()) {
            return Result.failure(IllegalStateException("No accounts to preflight."))
        }
        for (account in accounts) {
            if (!BuildConfig.USE_HEALTH_CONNECT_WRITER &&
                !accountRepository.hasFitnessPermissions(account)
            ) {
                return Result.failure(
                    PreflightException(
                        accountEmail = account.email.orEmpty(),
                        message = "Google Fit permissions incomplete.",
                    ),
                )
            }
            val ensureResult = fitWriter.ensureDataSources(account)
            if (ensureResult.isFailure) {
                return Result.failure(
                    PreflightException(
                        accountEmail = account.email.orEmpty(),
                        message = ensureResult.exceptionOrNull()?.message
                            ?: "DataSource setup failed.",
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
