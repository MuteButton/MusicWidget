# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep MediaSession and related classes
-keep class android.media.session.** { *; }
-keep class android.service.notification.NotificationListenerService { *; }

# Keep notification listener service
-keep class com.example.mediawidget.MediaListenerService { *; }

# Keep widget provider
-keep class com.example.mediawidget.MediaWidgetProvider { *; }

# Keep RemoteViews reflection targets
-keepclassmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep Palette library
-keep class androidx.palette.graphics.** { *; }

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep setters in Views so that animations can still work
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

# Preserve enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelables
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep broadcast receivers
-keep class * extends android.content.BroadcastReceiver

# Keep AppWidget related classes
-keep class * extends android.appwidget.AppWidgetProvider

# Preserve annotations
-keepattributes *Annotation*

# For Kotlin
-keep class kotlin.Metadata { *; }
