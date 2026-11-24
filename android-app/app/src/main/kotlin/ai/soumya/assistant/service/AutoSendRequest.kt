package ai.soumya.assistant.service

/**
 * One-shot auto-send request, shared between [ToolExecutor] (which arms it
 * when the user asked for a WhatsApp message / Facebook post with Auto-Send
 * ON) and [SoumyaAccessibilityService] (which performs the single click).
 *
 * SAFETY RULES (hard):
 * - A request is armed for ONE target package only (com.whatsapp or
 *   com.facebook.katana) and expires after 30 seconds.
 * - The service clicks Send/Post ONLY while a request for that exact
 *   package is active. At any other time, in any other app, Send/Post
 *   buttons are NEVER auto-clicked.
 * - The request is consumed on success and cleared on timeout / toggle-off,
 *   so one request can never send twice.
 * - Auto-send only ever sends a message the user themselves dictated, from
 *   the user's own account. It never touches passwords, OTPs or payments.
 */
object AutoSendRequest {

    data class Outcome(val success: Boolean, val message: String)

    const val TIMEOUT_MS: Long = 30_000L

    const val WHATSAPP_PACKAGE: String = "com.whatsapp"
    const val FACEBOOK_PACKAGE: String = "com.facebook.katana"

    private data class Request(val targetPackage: String, val createdAt: Long)

    @Volatile
    private var pending: Request? = null

    /** Result of the last armed request, once the service reports it. */
    @Volatile
    var outcome: Outcome? = null
        private set

    /** Arm a one-shot request for [targetPackage]. Clears any old outcome. */
    fun arm(targetPackage: String) {
        outcome = null
        pending = Request(targetPackage, System.currentTimeMillis())
    }

    /** Clear any pending request (timeout, toggle-off, new request). */
    fun clear() {
        pending = null
    }

    /** True only while a live (unexpired) request exists for [pkg]. */
    fun isActiveFor(pkg: String): Boolean {
        val request = pending ?: return false
        if (request.targetPackage != pkg) return false
        if (System.currentTimeMillis() - request.createdAt > TIMEOUT_MS) {
            pending = null
            return false
        }
        return true
    }

    /** Called by the AccessibilityService after it clicked Send/Post. */
    fun complete(success: Boolean, message: String) {
        pending = null
        outcome = Outcome(success, message)
    }
}
