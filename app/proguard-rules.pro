# PdfBox-Android: uses reflection and loads resources by class name.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
-dontwarn org.apache.commons.logging.**
-keep class com.tom_roush.** { *; }
-keep class org.apache.fontbox.** { *; }
-keepclassmembers class * implements java.io.Serializable { *; }

# ML Kit / Play services ship their own rules; keep the text recognizer options just in case.
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.**

# Keep line numbers so crash reports point at the right place.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
