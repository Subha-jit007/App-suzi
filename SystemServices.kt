package com.nova.ai.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * ═══════════════════════════════════════
 * NOVA ACCESSIBILITY SERVICE
 * ═══════════════════════════════════════
 * Legal uses only:
 *  - Inserting text into focused input fields
 *  - Reading screen content for context
 *  - Performing back/home/recents gestures
 *
 * NOT used for: unlocking phone, bypassing security
 */
class NovaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: NovaAccessibilityService? = null
            private set

        // Observable for keyboard auto-type
        val textToInsert = MutableSharedFlow<String>()

        /**
         * Inserts text into the currently focused input field.
         * Works in any app — WhatsApp, SMS, Notes, etc.
         */
        fun typeText(text: String) {
            textToInsert.tryEmit(text)
        }

        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }

        // Observe text injection requests
        // (launched from VoiceListenerService via broadcast)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Read screen context if needed for AI
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Types text into the currently focused node.
     * Call from VoiceListenerService when "type: ..." command received.
     */
    fun insertTextIntoFocusedField(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        root.recycle()
        return success
    }

    /** Performs the global back action */
    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Goes to home screen */
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Opens recents */
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
}


/**
 * ═══════════════════════════════════════
 * NOVA DEVICE ADMIN RECEIVER
 * ═══════════════════════════════════════
 * ONLY used for: lockNow() — locking the screen.
 * Cannot and does not bypass lock screen.
 */
class NovaDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // Device admin enabled — lock screen command now available
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Device admin disabled
    }
}


/**
 * ═══════════════════════════════════════
 * BOOT RECEIVER
 * ═══════════════════════════════════════
 * Restarts NOVA voice service after phone reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // Restart the always-listening service after boot
            val serviceIntent = VoiceListenerService.startIntent(context)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
