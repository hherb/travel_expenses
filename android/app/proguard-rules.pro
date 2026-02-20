# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep shared module serializable classes
-keep,includedescriptorclasses class com.travelexpenses.**$$serializer { *; }
-keepclassmembers class com.travelexpenses.** {
    *** Companion;
}
-keepclasseswithmembers class com.travelexpenses.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit
-keep class com.google.mlkit.** { *; }
