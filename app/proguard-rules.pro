# ── UberMax — reglas ProGuard/R8 ──────────────────────────────────────────────
# Añadidas para evitar fallos con R8 de Room, Hilt, OkHttp, Maps y corutinas.

# ---- Room ----
# Keep generated DAO/Database implementation classes (Room_*).
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.ubermax.app.data.db.** { *; }

# Room invokes entities via reflection for some queries: keep fields of entities.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.Provides class * { *; }

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ---- Google Maps / play-services ----
# play-services consumer rules usually cover this; keeping is safe.
-keep class com.google.android.gms.maps.** { *; }

# ---- Coroutines / kotlinx ----
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ---- Accidentals / misc ----
# Keep range of framework methods used reflectively via mutation of View.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod