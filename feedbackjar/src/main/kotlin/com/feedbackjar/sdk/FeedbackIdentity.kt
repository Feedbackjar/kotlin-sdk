package com.feedbackjar.sdk

/**
 * Submitter identity (name/email) remembered across [FeedbackJar.submit] calls.
 */
data class FeedbackIdentity(
    val name: String?,
    val email: String?,
)
