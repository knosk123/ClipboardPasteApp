package com.paste.clipboard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PasteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PasteA11yService"
        var instance: PasteAccessibilityService? = null
            private set
        var isRunning: Boolean = false
            private set
    }

    val typingHelper = TypingHelper()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for our use case, but required to override
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        typingHelper.cancel()
        instance = null
        isRunning = false
        Log.d(TAG, "Accessibility service destroyed")
    }

    fun getRootNode(): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get root node", e)
            null
        }
    }

    fun typeText(text: String, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        val root = getRootNode() ?: return false
        typingHelper.onComplete = onComplete
        return typingHelper.startTyping(root, text)
    }
}
