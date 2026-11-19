# Reglas ProGuard para AnyWall: Universal Converter
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

-keep class org.zwobble.mammoth.** { *; }
-dontwarn org.zwobble.mammoth.**

-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }

-keep class de.sciss.jump3r.** { *; }
-dontwarn de.sciss.jump3r.lowlevel.LameEncoder

-keep class android.print.** { *; }
