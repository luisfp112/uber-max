package com.ubermax.app.data.db.dao

import androidx.room.*
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistZoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zone: BlacklistZoneEntity)

    @Delete
    suspend fun delete(zone: BlacklistZoneEntity)

    @Query("SELECT * FROM blacklist_zone ORDER BY name ASC")
    suspend fun getAll(): List<BlacklistZoneEntity>

    @Query("SELECT * FROM blacklist_zone ORDER BY name ASC")
    fun getAllFlow(): Flow<List<BlacklistZoneEntity>>

    @Query("SELECT name FROM blacklist_zone")
    suspend fun getAllNames(): List<String>

    @Query("DELETE FROM blacklist_zone WHERE id = :id")
    suspend fun deleteById(id: Int)
}
