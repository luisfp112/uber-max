package com.ubermax.app.di

import android.content.Context
import androidx.room.Room
import com.ubermax.app.data.db.AppDatabase
import com.ubermax.app.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ubermax_db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9
            )
            .enableMultiInstanceInvalidation()
            .build()
    }

    @Provides
    fun provideTripLogDao(db: AppDatabase): TripLogDao = db.tripLogDao()

    @Provides
    fun provideVehicleConfigDao(db: AppDatabase): VehicleConfigDao = db.vehicleConfigDao()

    @Provides
    fun provideFilterRulesDao(db: AppDatabase): FilterRulesDao = db.filterRulesDao()

    @Provides
    fun provideBlacklistDao(db: AppDatabase): BlacklistDao = db.blacklistDao()

    @Provides
    fun provideBlacklistZoneDao(db: AppDatabase): BlacklistZoneDao = db.blacklistZoneDao()

    @Provides
    fun provideAppSettingsDao(db: AppDatabase): AppSettingsDao = db.appSettingsDao()
}
