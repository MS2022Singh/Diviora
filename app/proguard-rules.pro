# kotlinx.serialization: keep serializers for routine JSON models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.diviora.music.** { *** Companion; }
-keepclasseswithmembers class com.diviora.music.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.diviora.music.**$$serializer { *; }
