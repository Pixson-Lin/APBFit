package com.pixsonlin.apbfit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ScreenOnReceiver(
    private val scope: CoroutineScope,
    private val onScreenOn: suspend () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SCREEN_ON) return
        scope.launch { onScreenOn() }
    }

    companion object {
        fun register(
            context: Context,
            scope: CoroutineScope,
            onScreenOn: suspend () -> Unit,
        ): ScreenOnReceiver {
            val receiver = ScreenOnReceiver(scope, onScreenOn)
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            return receiver
        }

        fun unregister(context: Context, receiver: ScreenOnReceiver?) {
            if (receiver != null) {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }
}
