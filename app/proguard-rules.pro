# Keep kotlinx.serialization metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.vela.chat.**$$serializer { *; }
-keepclassmembers class com.vela.chat.** {
    *** Companion;
}
-keepclasseswithmembers class com.vela.chat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# PdfBox-Android pulls in some optional desktop classes that are absent on Android.
-dontwarn org.bouncycastle.**
-dontwarn javax.**
-dontwarn org.apache.**
-keep class com.tom_roush.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# PDFBox JP2 optional dependencies
-dontwarn com.gemalto.jp2.**

# Google Errorprone compile-only annotations
-dontwarn com.google.errorprone.annotations.**

