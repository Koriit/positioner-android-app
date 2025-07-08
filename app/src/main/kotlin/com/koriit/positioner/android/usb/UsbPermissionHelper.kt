package com.koriit.positioner.android.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object UsbPermissionHelper {
    private const val ACTION_USB_PERMISSION = "com.koriit.positioner.android.USB_PERMISSION"

    suspend fun requestPermission(context: Context, manager: UsbManager, device: UsbDevice): Boolean {
        return suspendCancellableCoroutine { cont ->
            val intent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACTION_USB_PERMISSION) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        context.unregisterReceiver(this)
                        cont.resume(granted)
                    }
                }
            }
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_USB_PERMISSION),
                Context.RECEIVER_NOT_EXPORTED
            )
            manager.requestPermission(device, intent)
        }
    }

    fun requestPermissionAsync(context: Context, manager: UsbManager, device: UsbDevice) {
        val intent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        manager.requestPermission(device, intent)
    }
}
