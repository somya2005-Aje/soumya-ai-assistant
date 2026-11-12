package ai.soumya.assistant.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional automation service. Disabled by default — the user enables it
 * manually in system Accessibility settings. It may inspect accessible
 * screen content and interact with supported buttons/scroll/text fields.
 * Sensitive actions always need confirmation in the app first.
 * It NEVER bypasses passwords, OTPs, biometrics or payment confirmations.
 */
class SoumyaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastEventDescription: String = "No events yet"
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val desc = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                "Opened: ${event.packageName} / ${event.className}"
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                "Tapped: ${event.text?.joinToString()}"
            else -> return
        }
        lastEventDescription = desc
    }

    override fun onInterrupt() {
        // Nothing to clean up.
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    /** Find a clickable node whose visible text matches [label] and click it. */
    fun clickByText(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(label)
        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    val ok = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (ok) return true
                }
                current = current.parent
            }
        }
        return false
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
}
