package com.feedbackjar.sdk.internal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object MetadataCollector {

    fun collect(context: Context): JsonObject {
        val displayMetrics = context.resources.displayMetrics
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        @Suppress("DEPRECATION")
        val versionCode = packageInfo?.versionCode?.toLong() ?: -1L

        return buildJsonObject {
            put("os", buildJsonObject {
                put("name", "Android")
                put("version", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
            })
            put("device", buildJsonObject {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("brand", Build.BRAND)
                put("type", if (isTablet(context)) "tablet" else "mobile")
            })
            put("screen", buildJsonObject {
                put("widthPx", displayMetrics.widthPixels)
                put("heightPx", displayMetrics.heightPixels)
                put("density", displayMetrics.density)
                put("dpi", displayMetrics.densityDpi)
            })
            put("locale", buildJsonObject {
                put("language", Locale.getDefault().language)
                put("country", Locale.getDefault().country)
                put("timezone", TimeZone.getDefault().id)
            })
            put("app", buildJsonObject {
                put("packageName", context.packageName)
                put("versionName", packageInfo?.versionName ?: "unknown")
                put("versionCode", versionCode)
            })
            put("sdk", SdkInfo.NAME)
            put("sdkVersion", SdkInfo.VERSION)
            put("timestamp", utcNow())
        }
    }

    private fun isTablet(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= 600

    private fun utcNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
