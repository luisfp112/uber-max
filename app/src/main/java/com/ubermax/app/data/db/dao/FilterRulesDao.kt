package com.ubermax.app.data.db.dao

import androidx.room.*
import com.ubermax.app.data.db.entity.FilterRulesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterRulesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rules: FilterRulesEntity)

    @Query("SELECT * FROM filter_rules WHERE id = 1")
    suspend fun getRules(): FilterRulesEntity?

    @Query("SELECT * FROM filter_rules WHERE id = 1")
    fun getRulesFlow(): Flow<FilterRulesEntity?>
}
