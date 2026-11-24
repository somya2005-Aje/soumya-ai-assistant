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
 *
 * AUTO-SEND (v1.2.0, user order): when the user turned ON the one-time
 * WhatsApp/Facebook Auto-Send toggle AND asked for a message/post, the
 * service clicks the Send/Post button — but ONLY while an [AutoSendRequest]
 * is armed for that exact package, and only once per request. At any other
 * time, in any other app, Send/Post buttons are NEVER auto-clicked.
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
        // AUTO-SEND: only while a request is armed for this exact package.
        // Window-content events are needed too — the Send button often
        // appears after the chat/composer finishes loading.
        val pkg = event.packageName?.toString()
        if (pkg != null && AutoSendRequest.isActiveFor(pkg) &&
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
            tryAutoSend(pkg)
        }
        val desc = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                "Opened: ${event.packageName} / ${event.className}"
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                "Tapped: ${event.text?.joinToString()}"
            else -> return
        }
        lastEventDescription = desc
    }

    /**
     * Attempts the one pending auto-send click for [pkg]. Runs only when
     * [AutoSendRequest.isActiveFor] was true. On success the request is
     * consumed (one request = at most one click, ever).
     */
    private fun tryAutoSend(pkg: String) {
        val root = rootInActiveWindow ?: return
        val clicked = when (pkg) {
            AutoSendRequest.WHATSAPP_PACKAGE -> clickWhatsAppSend(root)
            AutoSendRequest.FACEBOOK_PACKAGE -> clickFacebookPost(root)
            else -> false
        }
        if (clicked) {
            val message = when (pkg) {
                AutoSendRequest.WHATSAPP_PACKAGE ->
                    "WhatsApp message bhej diya ✓ (Auto-Send ON tha)"
                else -> "Facebook post kar diya ✓ (Auto-Send ON tha)"
            }
            AutoSendRequest.complete(success = true, message = message)
        }
    }

    /**
     * WhatsApp Send button. Tries, in order: the known send-button view id
     * (com.whatsapp:id/send), then a node whose content-description is
     * "Send". The id is WhatsApp-version dependent — if WhatsApp changes it,
     * the fallback still works, and if both miss the executor's 30s timeout
     * falls back to composer mode instead of claiming success.
     */
    private fun clickWhatsAppSend(root: AccessibilityNodeInfo): Boolean {
        val byId = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        for (node in byId) {
            if (clickNodeOrClickableAncestor(node)) return true
        }
        return clickByContentDescription(root, "Send")
    }

    /**
     * Facebook Post button. No stable public resource id is known for it
     * (Facebook changes ids across versions), so this matches the visible
     * "Post" label / content-description. If it is not found, the executor
     * falls back to composer mode — never a false success.
     */
    private fun clickFacebookPost(root: AccessibilityNodeInfo): Boolean {
        return clickByContentDescription(root, "Post")
    }

    /** Clicks the node itself if clickable, else its nearest clickable ancestor. */
    private fun clickNodeOrClickableAncestor(start: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = start
        while (current != null) {
            if (current.isClickable) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            current = current.parent
        }
        return false
    }

    /** Recursively finds a clickable node whose content-description or text matches [label]. */
    private fun clickByContentDescription(root: AccessibilityNodeInfo, label: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString()
            val text = node.text?.toString()
            if (desc.equals(label, ignoreCase = true) || text.equals(label, ignoreCase = true)) {
                if (clickNodeOrClickableAncestor(node)) return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
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
