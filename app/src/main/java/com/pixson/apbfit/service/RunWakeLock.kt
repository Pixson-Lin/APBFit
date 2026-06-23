package com.pixson.apbfit.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

class RunWakeLock(context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var writeLock: PowerManager.WakeLock? = null
    private var sessionLock: PowerManager.WakeLock? = null

    fun acquireForWrite() {
        if (writeLock?.isHeld == true) return
        writeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "APBFit:Write",
        ).apply {
            acquire(WRITE_LOCK_TIMEOUT_MS)
        }
    }

    fun releaseWrite() {
        writeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        writeLock = null
    }

    fun acquireSession() {
        if (sessionLock?.isHeld == true) return
        sessionLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "APBFit:Session",
        ).apply {
            acquire(SESSION_LOCK_TIMEOUT_MS)
        }
    }

    fun releaseSession() {
        sessionLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        sessionLock = null
    }

    fun releaseAll() {
        releaseWrite()
        releaseSession()
    }

    companion object {
        private const val WRITE_LOCK_TIMEOUT_MS = 10 * 60 * 1_000L
        private const val SESSION_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1_000L
        private const val TAG = "APBFit_WakeLock"
    }
}
