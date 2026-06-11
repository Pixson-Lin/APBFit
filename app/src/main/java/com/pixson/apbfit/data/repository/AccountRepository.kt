package com.pixson.apbfit.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Sign-In and account management.
 * Full implementation is planned for Sprint 2.
 */
@Singleton
class AccountRepository @Inject constructor() {
    fun getActiveAccountId(): String? = null

    fun hasActiveAccount(): Boolean = getActiveAccountId() != null
}
