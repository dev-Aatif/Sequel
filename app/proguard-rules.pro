# ──────────────────────────────────────────────────────────────────────────
# Sequel — ProGuard / R8 Rules
# ──────────────────────────────────────────────────────────────────────────

# ── Global Attributes ────────────────────────────────────────────────────
# Keep metadata R8 needs for reflection, serialization, and generic types.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ── Kotlinx Serialization ────────────────────────────────────────────────
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all generated $$serializer classes and Companion objects in our package.
-keep,includedescriptorclasses class dev.sequel.app.**$$serializer { *; }
-keepclassmembers class dev.sequel.app.** {
    *** Companion;
}
-keepclasseswithmembers class dev.sequel.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Retrofit ─────────────────────────────────────────────────────────────
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# If using @SerialName / kotlinx-serialization-converter with Retrofit,
# keep the converter factory.
-keep class com.jakewharton.retrofit2.converter.kotlinx.serialization.** { *; }

# ── OkHttp ───────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# ── Supabase / Ktor ─────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn java.lang.management.**

# ── Room ─────────────────────────────────────────────────────────────────
# Keep Entity classes (Room reflects on constructors and fields).
-keep class dev.sequel.app.data.local.entity.** { *; }

# Keep DAO interfaces – Room generates implementations from these.
-keep interface dev.sequel.app.data.local.dao.** { *; }

# Keep TypeConverters (Room invokes these reflectively).
-keep class dev.sequel.app.data.local.converter.** { *; }

# ── Hilt-injected Workers ───────────────────────────────────────────────
# WorkManager + Hilt: keep @HiltWorker classes so the factory can find them.
-keep class dev.sequel.app.data.sync.** extends androidx.work.ListenableWorker { *; }
-keep class dev.sequel.app.data.worker.** extends androidx.work.ListenableWorker { *; }

# ── API DTOs (TMDB / Supabase) ───────────────────────────────────────────
# These data classes are deserialized from JSON; field names must survive.
-keep class dev.sequel.app.data.remote.tmdb.dto.** { *; }
-keep class dev.sequel.app.data.remote.supabase.dto.** { *; }

# Catch-all for any *Dto class elsewhere under remote/.
-keep class dev.sequel.app.data.remote.supabase.**Dto { *; }
-keep class dev.sequel.app.data.remote.tmdb.**Dto { *; }

# ── Enums used by Room / Serialization ───────────────────────────────────
-keepclassmembers enum dev.sequel.app.data.local.entity.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
