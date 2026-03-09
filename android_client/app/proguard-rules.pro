# Keep Retrofit DTOs accessed by Gson reflection.
-keep class com.coreline.cbot.data.zai.dto.** { *; }
-keepclassmembers class com.coreline.cbot.data.zai.dto.** { *; }

# Keep JNI entrypoints.
-keep class com.coreline.cbot.data.security.NativeSecrets { *; }

# Preserve notification service and application entry points.
-keep class com.coreline.cbot.CBotApplication { *; }
-keep class com.coreline.cbot.MyNotificationService { *; }
