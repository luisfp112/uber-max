# UberMax ProGuard Rules

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep data classes for Room
-keep class com.ubermax.app.data.db.entity.** { *; }
-keep class com.ubermax.app.domain.model.** { *; }
-keep class com.ubermax.app.data.db.dao.HourStat { *; }
-keep class com.ubermax.app.data.db.dao.ZoneStat { *; }
