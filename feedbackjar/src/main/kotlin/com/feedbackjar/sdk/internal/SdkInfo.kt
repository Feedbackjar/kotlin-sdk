package com.feedbackjar.sdk.internal

import com.feedbackjar.sdk.BuildConfig

/**
 * SDK identity, sent on every request as `X-FeedbackJar-SDK: <name>/<version>`
 * and mirrored into submission metadata (`sdk` / `sdkVersion`).
 *
 * [VERSION] comes from `BuildConfig`, which is set from the publish `VERSION`
 * env / the fallback in `build.gradle.kts`.
 */
internal object SdkInfo {
    const val NAME = "kotlin"
    val VERSION: String = BuildConfig.SDK_VERSION
    val IDENTIFIER: String get() = "$NAME/$VERSION"
}
