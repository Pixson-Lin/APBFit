package com.pixson.apbfit.domain.fit

import com.google.android.gms.auth.api.signin.GoogleSignInAccount

interface FitWriter {
    suspend fun ensureDataSources(account: GoogleSignInAccount): Result<Unit>

    suspend fun writeSegments(
        account: GoogleSignInAccount,
        segments: List<SegmentData>,
    ): Result<Unit>
}
