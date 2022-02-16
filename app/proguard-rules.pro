# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/harsha/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# This is not required because of the "Keep" annotation, keep for completeness
-keepclassmembers class io.github.muntashirakon.bcl.ControlFile {
   private *;
}
# This is safe for Android >= 2.0
-optimizations code/simplification/arithmetic

-optimizations !code/allocation/variable
-dontobfuscate