# APEX File Manager — R8 rules
# Compose + Coil work out of the box with recent AGP defaults.
# Keep the custom ImageLoader singleton factory if referenced reflectively.
-keep class coil.ImageLoader { *; }

# Keep ViewModel default constructors for the viewModelFactory initializer pattern.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# pdfbox-android: reflection-based text/rendering engine, needs its classes kept.
# The Android port relocates the Apache package to com.tom_roush.pdfbox.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**