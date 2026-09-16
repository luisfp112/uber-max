package com.ubermax.app.data.db.dao

import androidx.room.*
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistZoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zone: BlacklistZoneEntity): Long

    @Update
    suspend fun update(zone: BlacklistZoneEntity)

    @Delete
    suspend fun delete(zone: BlacklistZoneEntity)

    @Query("SELECT * FROM blacklist_zone ORDER BY name ASC")
    suspend fun getAll(): List<BlacklistZoneEntity>

    @Query("SELECT * FROM blacklist_zone ORDER BY name ASC")
    fun getAllFlow(): Flow<List<BlacklistZoneEntity>>

    @Query("SELECT * FROM blacklist_zone WHERE id = :id")
    suspend fun getById(id: Int): BlacklistZoneEntity?

    @Query("SELECT name FROM blacklist_zone")
    suspend fun getAllNames(): List<String>

    @Query("SELECT COUNT(*) FROM blacklist_zone")
    fun getZoneCountFlow(): Flow<Int>

    @Query("DELETE FROM blacklist_zone WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE blacklist_zone SET name = :name WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("UPDATE blacklist_zone SET polygon_json = :polygonJson WHERE id = :id")
    suspend fun updatePolygon(id: Int, polygonJson: String)

    @Query("UPDATE blacklist_zone SET extracted_keywords_json = :keywordsJson WHERE id = :id")
    suspend fun updateExtractedKeywords(id: Int, keywordsJson: String)
}