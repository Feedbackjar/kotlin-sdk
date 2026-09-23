package com.feedbackjar.sdk

/**
 * Widget configuration for this organization, as set in the FeedbackJar dashboard.
 *
 * @property collectName   Whether the org asks submitters for their name ("Ask for Name").
 * @property collectEmail  Whether the org asks submitters for their email ("Ask for Email").
 * @property allowVotes    Whether guest upvoting is enabled for this project. Use this to
 *                         show/hide your own vote UI.
 * @property allowComments Whether guest commenting is enabled for this project. Use this to
 *                         show/hide your own comment UI.
 */
data class WidgetConfig(
    val collectName: Boolean,
    val collectEmail: Boolean,
    val allowVotes: Boolean,
    val allowComments: Boolean,
)
