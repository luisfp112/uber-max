package com.ubermax.app.data.db.dao

import androidx.room.*
import com.ubermax.app.data.db.entity.TripLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tripLog: TripLogEntity): Long

    @Query("SELECT * FROM trip_log ORDER BY timestamp DESC")
    fun getAllTripsFlow(): Flow<List<TripLogEntity>>

    @Query("SELECT * FROM trip_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTrips(limit: Int = 50): List<TripLogEntity>

    @Query("SELECT * FROM trip_log WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC")
    suspend fun getTripsForDay(startOfDay: Long, endOfDay: Long): List<TripLogEntity>

    @Query("SELECT * FROM trip_log WHERE day_of_week = :dayOfWeek ORDER BY timestamp DESC")
    suspend fun getTripsByDayOfWeek(dayOfWeek: String): List<TripLogEntity>

    @Query("SELECT * FROM trip_log WHERE decision = 'ACCEPT' ORDER BY timestamp DESC")
    fun getAcceptedTripsFlow(): Flow<List<TripLogEntity>>

    @Query("""
        SELECT hour_of_day, AVG(net_profit) as avgProfit, COUNT(*) as count
        FROM trip_log
        WHERE decision = 'ACCEPT'
        GROUP BY hour_of_day
        ORDER BY avgProfit DESC
    """)
    suspend fun getBestHours(): List<HourStat>

    @Query("""
        SELECT destination, AVG(profit_per_km) as avgProfitPerKm, COUNT(*) as count
        FROM trip_log
        WHERE decision = 'ACCEPT' AND destination != ''
        GROUP BY destination
        ORDER BY avgProfitPerKm DESC
        LIMIT 20
    """)
    suspend fun getBestZones(): List<ZoneStat>

    @Query("SELECT COUNT(*) FROM trip_log WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getTotalOffersForDay(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM trip_log WHERE timestamp >= :startOfDay AND timestamp < :endOfDay AND decision = 'ACCEPT'")
    suspend fun getAcceptedOffersForDay(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT SUM(net_profit) FROM trip_log WHERE timestamp >= :startOfDay AND timestamp < :endOfDay AND decision = 'ACCEPT'")
    suspend fun getTotalNetProfitForDay(startOfDay: Long, endOfDay: Long): Double?

    @Query("DELETE FROM trip_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM trip_log ORDER BY timestamp DESC")
    suspend fun getAllTrips(): List<TripLogEntity>
}

/** Aggregate stat for hourly analysis */
data class HourStat(
    val hour_of_day: Int,
    val avgProfit: Double,
    val count: Int
)

/** Aggregate stat for zone analysis */
data class ZoneStat(
    val destination: String,
    val avgProfitPerKm: Double,
    val count: Int
)
