# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep source file and line numbers for CrashHandler stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Coroutines & Ktor CIO R8 compatibility
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**
-dontwarn org.slf4j.**

# OkHttp optional JVM TLS providers (not present on Android)
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

