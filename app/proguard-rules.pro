# Apache MINA SSHD
-keep class org.apache.sshd.** { *; }
-dontwarn org.apache.sshd.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SLF4J / Logback
-dontwarn ch.qos.logback.**
-dontwarn org.slf4j.**

# JNI
-keepclasseswithmembernames class * {
    native <methods>;
}
