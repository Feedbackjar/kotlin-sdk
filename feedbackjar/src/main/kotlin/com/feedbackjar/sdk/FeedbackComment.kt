package com.feedbackjar.sdk

/**
 * A public comment on a feedback post. Threads are two levels deep — a root
 * comment carries its direct replies in [replies], and those replies always
 * have an empty [replies] list of their own.
 *
 * @property authorName The display name shown for the comment's author.
 * @property authorRole Org role of the author when they're a team member
 *                      (e.g. `owner`, `admin`, `member`), else `null` for guests.
 * @property isBot      Whether the comment was posted by an automated account.
 * @property parentId   The root comment's id when this is a reply, else `null`.
 * @property createdAt  ISO-8601 timestamp.
 * @property replies    Direct replies to this comment (empty for replies themselves).
 */
data class FeedbackComment(
    val id: String,
    val content: String,
    val authorName: String,
    val authorRole: String?,
    val isBot: Boolean,
    val parentId: String?,
    val createdAt: String,
    val replies: List<FeedbackComment>,
)

/**
 * A page of a post's public comment thread.
 *
 * @property comments   Root comments, each with their nested [FeedbackComment.replies].
 * @property nextCursor Pass back into [FeedbackJar.listComments] for the next page;
 *                      `null` when there are no more pages.
 */
data class FeedbackCommentListResult(
    val comments: List<FeedbackComment>,
    val nextCursor: String?,
)

/**
 * Result of adding a public comment.
 *
 * @property id The new comment's id.
 */
data class CommentResponse(
    val id: String,
)
