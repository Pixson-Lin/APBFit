package com.pixsonlin.apbfit.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.pixsonlin.apbfit.data.prefs.AccountPrefs
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

    private val _accountRevision = MutableStateFlow(0)
    val accountRevision: StateFlow<Int> = _accountRevision.asStateFlow()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    suspend fun initialize() {
        mutex.withLock {
            enforceSingleAccountPolicy()
            restoreCachedAccounts()
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

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
            enforceSingleAccount(account.id!!)
            accountPrefs.setActiveAccountId(account.id!!)
            _activeAccount.value = account
            bumpAccounts()
            account
        }.onFailure { error ->
            Log.e(TAG, "handleSignInResult failed: ${error.message}", error)
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

    fun hasActiveAccount(): Boolean = _activeAccount.value != null

    fun getActiveAccountId(): String? = _activeAccount.value?.id

    fun getAccountById(accountId: String): GoogleSignInAccount? = accountCache[accountId]

    fun requireActiveAccount(): GoogleSignInAccount =
        _activeAccount.value ?: error("No active account")

    private fun enforceSingleAccountPolicy() {
        val known = accountPrefs.getKnownAccountIds()
        if (known.size <= 1) return
        val activeId = accountPrefs.getActiveAccountId() ?: known.first()
        accountPrefs.setKnownAccountIds(setOf(activeId))
        known.filter { it != activeId }.forEach { accountCache.remove(it) }
    }

    private fun enforceSingleAccount(accountId: String) {
        accountCache.keys.filter { it != accountId }.forEach { accountCache.remove(it) }
        accountPrefs.setKnownAccountIds(setOf(accountId))
    }

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

    companion object {
        private const val TAG = "APBFit_Account"
    }
}
