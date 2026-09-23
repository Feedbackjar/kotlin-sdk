-keep class com.feedbackjar.sdk.FeedbackJar { *; }
-keep class com.feedbackjar.sdk.FeedbackResponse { *; }
-keep class com.feedbackjar.sdk.FeedbackPost { *; }
-keep class com.feedbackjar.sdk.FeedbackListResult { *; }

# Prebuilt Views UI entry points.
-keep class com.feedbackjar.sdk.ui.FeedbackJarView { *; }
-keep class com.feedbackjar.sdk.ui.FeedbackJarActivity { *; }

# The Compose UI (com.feedbackjar.sdk.ui.compose.*) references Compose via a
# compileOnly dependency. Apps that use only the Views UI have no Compose on the
# classpath — silence R8's missing-class warnings for that unused code path.
-dontwarn androidx.compose.**
-dontwarn com.feedbackjar.sdk.ui.compose.**
