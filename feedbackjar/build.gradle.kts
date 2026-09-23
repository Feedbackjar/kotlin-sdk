import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.feedbackjar.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")

        // Sent on every request as `X-FeedbackJar-SDK: kotlin/<version>`.
        buildConfigField(
            "String",
            "SDK_VERSION",
            "\"${System.getenv("VERSION") ?: "1.7.0"}\"",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The prebuilt Compose UI (com.feedbackjar.sdk.ui.compose.FeedbackJarBoard) is
    // opt-in: Compose is compileOnly (see dependencies) so the published .aar carries
    // no transitive Compose dependency. Apps that call the Views UI never pull Compose.
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Compose — compileOnly on purpose: consumers of the Views UI get zero Compose
    // dependency, and callers of FeedbackJarBoard already have Compose on the classpath.
    val compose = "1.7.8"
    compileOnly("androidx.compose.runtime:runtime:$compose")
    compileOnly("androidx.compose.foundation:foundation:$compose")
    compileOnly("androidx.compose.ui:ui:$compose")
    compileOnly("androidx.compose.material3:material3:1.3.2")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = "com.feedbackjar",
        artifactId = "sdk",
        version = System.getenv("VERSION") ?: "1.7.0",
    )

    pom {
        name.set("FeedbackJar Android SDK")
        description.set("Android SDK for anonymous feedback submission and listing via FeedbackJar")
        url.set("https://github.com/mcnaveen/feedbackjar-monorepo")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("mcnaveen")
                name.set("Naveen MC")
                url.set("https://github.com/mcnaveen")
            }
        }
        scm {
            url.set("https://github.com/mcnaveen/feedbackjar-monorepo")
            connection.set("scm:git:git://github.com/mcnaveen/feedbackjar-monorepo.git")
            developerConnection.set("scm:git:ssh://git@github.com/mcnaveen/feedbackjar-monorepo.git")
        }
    }
}
