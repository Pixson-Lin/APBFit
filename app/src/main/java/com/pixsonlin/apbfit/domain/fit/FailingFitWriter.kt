package com.pixsonlin.apbfit.domain.fit

import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/**
 * Test double that always fails writes. Used for debug verification and unit tests.
 */
class FailingFitWriter(
    private val message: String = "Injected write failure",
) : FitWriter {
    override suspend fun ensureDataSources(account: GoogleSignInAccount): Result<Unit> =
        Result.success(Unit)

    override suspend fun writeSegments(
        account: GoogleSignInAccount,
        segments: List<SegmentData>,
    ): Result<Unit> = Result.failure(IllegalStateException(message))
}
