package com.example.focusnfc

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class BlockAccessibilityService : AccessibilityService() {

    private var lastBlockTime = 0L

    // Payment apps we completely IGNORE so they have zero conflict
    private val paymentApps = setOf(
        "net.one97.paytm",
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
        "in.org.npci.upiapp",
        "com.sbi.upi"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = null // Monitor all apps
            notificationTimeout = 10
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // NEVER touch our own app or payment apps
        if (packageName == applicationContext.packageName || paymentApps.contains(packageName)) return

        // If focus session is active and user tries to open a blocked app (Instagram, YouTube, etc.)
        if (FocusService.isSessionActive && FocusService.blockedApps.contains(packageName)) {
            val currentTime = System.currentTimeMillis()

            // Kick back to home screen
            performGlobalAction(GLOBAL_ACTION_HOME)

            // Bring FocusNFC to front to kill PiP and overlay completely
            if (currentTime - lastBlockTime > 800) {
                lastBlockTime = currentTime
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                Toast.makeText(applicationContext, "⛔ Focus Mode Active! App Blocked.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInterrupt() {}
}
