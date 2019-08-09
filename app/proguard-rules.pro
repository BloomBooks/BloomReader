# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in X:\bin\android-sdk-windows/tools/proguard/proguard-android.txt
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

# generate a file that can be useful in debugging conflicts
#-printconfiguration 'proguardConfiguration.txt'

# duplicate classes in these domains are pulled in from
# -libraryjars 'D:\tools\android\platforms\android-23\android.jar' and
# -libraryjars 'D:\tools\android\platforms\android-23\optional\org.apache.http.legacy.jar'
# according to https://stackoverflow.com/questions/33047806/proguard-duplicate-definition-of-library-class
# there's nothing to be done about it except ignore it.
-dontnote android.net.http.*
-dontnote org.apache.http.conn.*
-dontnote org.apache.http.conn.scheme.*
-dontnote org.apache.http.params.HttpParams

# We get a whole mess of warnings (over 200!) about classes not found. Apparently they are things
# needed for some parts of org.apache.commons.compress but not the tiny part we are using, since
# our code actually works without them. Proguard crashes when these warnings aren't suppressed,
# so I went ahead and suppressed them all.
-dontwarn org.apache.commons.compress.archivers.Lister
-dontwarn org.apache.commons.compress.archivers.sevenz.*
-dontwarn org.apache.commons.compress.archivers.zip.*
-dontwarn org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream
-dontwarn org.apache.commons.compress.compressors.lzma.*
-dontwarn org.apache.commons.compress.compressors.pack200.TempFileCachingStreamBridge
-dontwarn org.apache.commons.compress.compressors.xz.*
-dontwarn org.apache.commons.compress.compressors.zstandard.*
-dontwarn org.apache.commons.compress.parallel.*
-dontwarn org.apache.commons.compress.utils.*

# We're not hiding anything...we're open source!
-dontobfuscate
