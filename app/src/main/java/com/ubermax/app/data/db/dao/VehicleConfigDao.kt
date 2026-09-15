package com.ubermax.app.data.db.dao

import androidx.room.*
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: VehicleConfigEntity)

    @Query("SELECT * FROM vehicle_config WHERE id = 1")
    suspend fun getConfig(): VehicleConfigEntity?

    @Query("SELECT * FROM vehicle_config WHERE id = 1")
    fun getConfigFlow(): Flow<VehicleConfigEntity?>
}
