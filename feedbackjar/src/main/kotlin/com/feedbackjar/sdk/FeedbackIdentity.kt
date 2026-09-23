package com.feedbackjar.sdk

/**
 * Submitter identity remembered across [FeedbackJar] calls.
 *
 * [userId]/[signature]/[timestamp] are set only via the **verified**
 * `FeedbackJar.setIdentity(userId, email, signature, timestamp, ...)` overload —
 * when present (and not expired), vote, comment, and submit all attach to this
 * real, server-verified user instead of the install's anonymous id.
 */
data class FeedbackIdentity(
    val name: String? = null,
    val email: String? = null,
    val userId: String? = null,
    val signature: String? = null,
    /** Milliseconds since epoch — the exact value your backend signed. */
    val timestamp: Long? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatar: String? = null,
)
