package com.feedbackjar.sdk.internal

import android.content.Context
import java.util.UUID

/**
 * A stable per-install anonymous id, persisted in its own [android.content.SharedPreferences]
 * file so [com.feedbackjar.sdk.FeedbackJar.clearIdentity] never wipes it.
 *
 * Used to attribute guest votes/comments to the same install. This is **not** a
 * device id — it's a fresh random UUID generated on first use that resets on
 * reinstall or clear-data. `ANDROID_ID` and advertising ids are never used.
 *
 * Sent as the `X-FeedbackJar-Anon-Id` header on every mutating call.
 */
internal object AnonId {

    private const val PREFS_NAME = "com.feedbackjar.sdk.anon"
    private const val KEY = "com.feedbackjar.sdk.anonId"

    @Volatile
    private var cached: String? = null

    /** The anonymous id for this install, generating and persisting one on first use. */
    fun get(context: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY, null)
            val id = existing ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY, it).apply()
            }
            cached = id
            return id
        }
    }
}
