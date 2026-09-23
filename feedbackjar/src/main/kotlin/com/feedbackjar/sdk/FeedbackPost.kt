package com.feedbackjar.sdk

data class FeedbackPost(
    val id: String,
    val title: String,
    val content: String,
    val type: String,
    val status: String,
    val slug: String,
    val boardId: String,
    val voteCount: Int,
    val commentCount: Int,
    val upvotes: Int,
    /** Whether this install's anonymous id has upvoted this post. */
    val hasVoted: Boolean = false,
    val authorName: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class FeedbackListResult(
    val posts: List<FeedbackPost>,
    val nextCursor: String?,
)
