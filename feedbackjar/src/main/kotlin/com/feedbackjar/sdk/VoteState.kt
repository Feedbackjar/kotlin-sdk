package com.feedbackjar.sdk

/**
 * Upvote state for a single post, as seen by this install's anonymous guest.
 *
 * @property upvotes  Current upvote count for the post.
 * @property hasVoted Whether this install's anonymous id has upvoted the post.
 */
data class VoteState(
    val upvotes: Int,
    val hasVoted: Boolean,
)
