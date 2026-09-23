package com.feedbackjar.sdk

data class FeedbackResponse(
    val postId: String,
    val title: String,
    val type: String,
    val boardId: String,
)
