package com.pixson.apbfit.domain.fit

import kotlin.random.Random

fun seedForAccount(sessionId: String, accountId: String): Random {
    val seed = (sessionId + accountId).hashCode().toLong()
    return Random(seed)
}
