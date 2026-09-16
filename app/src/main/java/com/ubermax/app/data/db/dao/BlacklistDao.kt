package com.ubermax.app.data.db.dao

import androidx.room.*
import com.ubermax.app.data.db.entity.BlacklistEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BlacklistEntryEntity)

    @Delete
    suspend fun delete(entry: BlacklistEntryEntity)

    @Query("SELECT * FROM blacklist_entry ORDER BY keyword ASC")
    fun getAllEntriesFlow(): Flow<List<BlacklistEntryEntity>>

    @Query("SELECT * FROM blacklist_entry")
    suspend fun getAllEntries(): List<BlacklistEntryEntity>

    @Query("SELECT keyword FROM blacklist_entry")
    suspend fun getAllKeywords(): List<String>

    @Query("SELECT * FROM blacklist_entry ORDER BY keyword ASC")
    suspend fun getAll(): List<BlacklistEntryEntity>

    @Query("DELETE FROM blacklist_entry WHERE reason LIKE :zoneNamePattern")
    suspend fun deleteByReasonPattern(zoneNamePattern: String)
}
