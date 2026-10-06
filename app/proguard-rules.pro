# kotlinx.serialization: conservar serializers generados de wisprkit.
-keepclassmembers class mx.diego.wisprkit.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class mx.diego.wisprkit.**$$serializer { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
