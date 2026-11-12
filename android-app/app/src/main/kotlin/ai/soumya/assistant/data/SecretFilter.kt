package ai.soumya.assistant.data

/** Never store passwords, OTPs or auth secrets in memory. */
object SecretFilter {

    private val patterns = listOf(
        Regex("(?i)\\b(password|passwd|pwd|otp|one[- ]?time[- ]?password|pin|secret|api[-_ ]?key|token|credit[- ]?card|cvv|bank[- ]?account|aadhaar|pan[- ]?card)\\b[^\\n]{0,40}"),
        Regex("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"), // card-like numbers
        Regex("\\b\\d{6}\\b") // bare 6-digit OTP-like code on its own line is handled by caller context
    )

    private val otpContext = Regex("(?i)\\b(otp|verification code|verification-code)\\b")

    /** True when the text looks like it contains a secret and must not be saved. */
    fun looksLikeSecret(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        // A bare 6-digit number alone is almost always an OTP.
        if (t.matches(Regex("\\d{6}"))) return true
        if (otpContext.containsMatchIn(t)) return true
        return patterns[0].containsMatchIn(t) || patterns[1].containsMatchIn(t)
    }

    /** Redact obvious secrets for display/export safety. */
    fun redact(text: String): String {
        var out = text
        out = patterns[1].replace(out, "•••• •••• •••• ••••")
        out = patterns[0].replace(out, "[secret hidden]")
        return out
    }
}
