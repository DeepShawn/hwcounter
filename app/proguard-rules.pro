# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.deepshawn.hwcounter.**$$serializer { *; }
-keepclassmembers class com.deepshawn.hwcounter.** {
    *** Companion;
}
-keepclasseswithmembers class com.deepshawn.hwcounter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.deepshawn.hwcounter.data.** { *; }
# ML Kit / zxing
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.zxing.**
