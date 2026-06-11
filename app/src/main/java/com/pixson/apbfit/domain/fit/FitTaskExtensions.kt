package com.pixson.apbfit.domain.fit

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal suspend fun <T> Task<T>.awaitWithTimeout(
    timeoutMs: Long = FitConstants.WRITE_TIMEOUT_MS,
): T = withTimeout(timeoutMs) { await() }
