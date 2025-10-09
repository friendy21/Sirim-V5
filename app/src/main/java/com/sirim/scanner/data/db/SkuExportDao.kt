package com.sirim.scanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SkuExportDao {
    @Query("SELECT * FROM sku_exports ORDER BY updated_at DESC")
    fun observeExports(): Flow<List<SkuExportRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SkuExportRecord): Long

    @Delete
    suspend fun delete(record: SkuExportRecord)
}
