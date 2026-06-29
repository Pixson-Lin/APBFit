package com.pixsonlin.apbfit.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.pixsonlin.apbfit.data.prefs.AccountPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountPrefs: AccountPrefs,
) {
    private val mutex = Mutex()
    private val accountCache = mutableMapOf<String, GoogleSignInAccount>()

    private val _activeAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val activeAccount: StateFlow<GoogleSignInAccount?> = _activeAccount.asStateFlow()

    private val _accountRevision = MutableStateFlow(0)
    val accountRevision: StateFlow<Int> = _accountRevision.asStateFlow()

    val fitnessOptions: FitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
        .build()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .addExtension(fitnessOptions)
            .build()
        GoogleSignIn.getClient(context, options)
    }

    suspend fun initialize() {
        mutex.withLock {
            restoreCachedAccounts()
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    /**
     * Clears the Google Play Services sign-in session (not [accountCache]) so the next
     * [getSignInIntent] shows the account picker instead of silently reusing the active account.
     */
    suspend fun getAddAccountIntent(): Intent {
        val activeEmail = _activeAccount.value?.email
        Log.d(TAG, "getAddAccountIntent: signing out GMS session (active=$activeEmail) to force picker")
        awaitGoogleSignOut()
        return googleSignInClient.signInIntent
    }

    /** Re-runs Google Sign-In to grant any missing Fitness OAuth scopes (e.g. READ). */
    fun getFitnessPermissionsIntent(): Intent = googleSignInClient.signInIntent

    suspend fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> = mutex.withLock {
        runCatching {
            if (data == null) {
                Log.w(TAG, "handleSignInResult: intent data is null (cancelled?)")
                error("Sign-in was cancelled.")
            }
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val alreadyKnown = account.id in accountPrefs.getKnownAccountIds()
            Log.d(
                TAG,
                "handleSignInResult: email=${account.email} id=${account.id} alreadyKnown=$alreadyKnown",
            )
            cacheAccount(account)
            accountPrefs.addKnownAccountId(account.id!!)
            accountPrefs.setActiveAccountId(account.id!!)
            _activeAccount.value = account
            bumpAccounts()
            account
        }.onFailure { error ->
            Log.e(TAG, "handleSignInResult failed: ${error.message}", error)
        }
    }

    suspend fun switchAccount(accountId: String): Result<Unit> = mutex.withLock {
        runCatching {
            val account = accountCache[accountId]
                ?: throw IllegalStateException("Account is not available. Sign in again.")
            accountPrefs.setActiveAccountId(accountId)
            _activeAccount.value = account
            bumpAccounts()
        }
    }

    suspend fun signOutAccount(accountId: String): Result<Unit> = mutex.withLock {
        runCatching {
            if (accountId !in accountPrefs.getKnownAccountIds()) {
                throw IllegalStateException("Account is not available. Sign in again.")
            }
            accountCache.remove(accountId)
            accountPrefs.removeKnownAccountId(accountId)
            val activeId = accountPrefs.getActiveAccountId()
            if (activeId == accountId) {
                val remaining = accountPrefs.getKnownAccountIds().firstOrNull()
                if (remaining != null) {
                    accountPrefs.setActiveAccountId(remaining)
                    _activeAccount.value = accountCache[remaining]
                } else {
                    accountPrefs.clearActiveAccountId()
                    _activeAccount.value = null
                    awaitGoogleSignOut()
                }
            }
            bumpAccounts()
        }
    }

    suspend fun signOutCurrentAccount() {
        mutex.withLock {
            val currentId = _activeAccount.value?.id
            googleSignInClient.signOut()
            if (currentId != null) {
                accountCache.remove(currentId)
                accountPrefs.removeKnownAccountId(currentId)
            }
            accountPrefs.clearActiveAccountId()
            _activeAccount.value = null
            bumpAccounts()
        }
    }

    fun getKnownAccounts(): List<GoogleSignInAccount> {
        val knownIds = accountPrefs.getKnownAccountIds()
        return knownIds.mapNotNull { accountCache[it] }
    }

    fun hasActiveAccount(): Boolean = _activeAccount.value != null

    fun getActiveAccountId(): String? = _activeAccount.value?.id

    fun getAccountById(accountId: String): GoogleSignInAccount? = accountCache[accountId]

    fun hasFitnessPermissions(account: GoogleSignInAccount = requireActiveAccount()): Boolean =
        GoogleSignIn.hasPermissions(account, fitnessOptions)

    suspend fun handleFitnessPermissionResult(data: Intent?): Result<GoogleSignInAccount> {
        val accountResult = handleSignInResult(data)
        return accountResult.fold(
            onSuccess = { account ->
                if (hasFitnessPermissions(account)) {
                    Result.success(account)
                } else {
                    Result.failure(
                        IllegalStateException("Google Fit permissions were not fully granted."),
                    )
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    fun requireActiveAccount(): GoogleSignInAccount =
        _activeAccount.value ?: error("No active account")

    private fun restoreCachedAccounts() {
        val lastSignedIn = GoogleSignIn.getLastSignedInAccount(context)
        if (lastSignedIn?.id != null) {
            cacheAccount(lastSignedIn)
            accountPrefs.addKnownAccountId(lastSignedIn.id!!)
            val activeId = accountPrefs.getActiveAccountId() ?: lastSignedIn.id!!
            accountPrefs.setActiveAccountId(activeId)
            _activeAccount.value = accountCache[activeId] ?: lastSignedIn
            bumpAccounts()
        }
    }

    private fun bumpAccounts() {
        _accountRevision.value++
    }

    private fun cacheAccount(account: GoogleSignInAccount) {
        val id = account.id ?: return
        accountCache[id] = account
    }

    private suspend fun awaitGoogleSignOut() = suspendCancellableCoroutine { cont ->
        googleSignInClient.signOut().addOnCompleteListener { cont.resume(Unit) }
    }

    companion object {
        private const val TAG = "APBFit_Account"
    }
}
