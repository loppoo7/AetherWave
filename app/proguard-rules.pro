# NewPipeExtractor loads its service and handler classes by name.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.**
# Room entities and DAOs are used through generated code.
-keep class com.aetherwave.data.local.** { *; }
# Keep stack trace line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
