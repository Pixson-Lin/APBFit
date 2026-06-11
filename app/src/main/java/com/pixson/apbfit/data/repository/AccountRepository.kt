package com.pixson.apbfit.data.repository

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.pixson.apbfit.data.prefs.AccountPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountPrefs: AccountPrefs,
) {
    private val mutex = Mutex()
    private val accountCache = mutableMapOf<String, GoogleSignInAccount>()

    private val _activeAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val activeAccount: StateFlow<GoogleSignInAccount?> = _activeAccount.asStateFlow()

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

    suspend fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> = mutex.withLock {
        runCatching {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            cacheAccount(account)
            accountPrefs.addKnownAccountId(account.id!!)
            accountPrefs.setActiveAccountId(account.id!!)
            _activeAccount.value = account
            account
        }
    }

    suspend fun switchAccount(accountId: String): Result<Unit> = mutex.withLock {
        runCatching {
            val account = accountCache[accountId]
                ?: throw IllegalStateException("Account is not available. Sign in again.")
            accountPrefs.setActiveAccountId(accountId)
            _activeAccount.value = account
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
        }
    }

    fun getKnownAccounts(): List<GoogleSignInAccount> {
        val knownIds = accountPrefs.getKnownAccountIds()
        return knownIds.mapNotNull { accountCache[it] }
    }

    fun hasActiveAccount(): Boolean = _activeAccount.value != null

    fun getActiveAccountId(): String? = _activeAccount.value?.id

    fun hasFitnessPermissions(account: GoogleSignInAccount = requireActiveAccount()): Boolean =
        GoogleSignIn.hasPermissions(account, fitnessOptions)

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
        }
    }

    private fun cacheAccount(account: GoogleSignInAccount) {
        val id = account.id ?: return
        accountCache[id] = account
    }
}
