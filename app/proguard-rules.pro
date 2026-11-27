# Reglas R8 / ProGuard para AnyWall: Universal Converter (v0.6)
# Mantiene los nombres legibles sin ofuscación mientras elimina los +11,900 iconos no usados de
# material-icons-extended y las clases PQC experimentales no referenciadas.
-dontobfuscate
-dontoptimize

# Preservar todo el código propio de AnyWall (incluyendo métodos JNI de LibArchiveRarBridge, entidades Room y Workers)
-keep class com.anywall.converter.** { *; }
-keep class android.print.** { *; }

# Preservar motores de documentos, imágenes, audio/video, QR y archivos comprimidos
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**

-keep class org.zwobble.** { *; }
-dontwarn org.zwobble.**

-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.tukaani.xz.** { *; }

-keep class de.sciss.jump3r.** { *; }
-dontwarn de.sciss.jump3r.**

-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
