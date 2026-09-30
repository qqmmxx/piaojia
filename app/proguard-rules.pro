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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 保留Apache POI相关类
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

# 保留XML相关类
-keep class org.openxmlformats.** { *; }
-keep class org.xml.** { *; }
-dontwarn org.openxmlformats.**
-dontwarn org.xml.**

# 保留Apache Commons相关类
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# 保留Log4j相关类
-keep class org.apache.logging.** { *; }
-dontwarn org.apache.logging.**

# 保留LambdaMetafactory相关类
-keep class java.lang.invoke.** { *; }
-dontwarn java.lang.invoke.**