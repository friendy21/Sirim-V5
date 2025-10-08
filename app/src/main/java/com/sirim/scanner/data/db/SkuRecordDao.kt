package com.sirim.scanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SkuRecordDao {
    @Query("SELECT * FROM sku_records ORDER BY created_at DESC")
    fun getAllRecords(): Flow<List<SkuRecord>>

    @Query("SELECT * FROM sku_records WHERE id = :id")
    suspend fun getRecordById(id: Long): SkuRecord?

    @Query("SELECT * FROM sku_records WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): SkuRecord?

    @Query(
        "SELECT * FROM sku_records WHERE barcode LIKE :query OR batch_no LIKE :query OR brand_trademark LIKE :query OR model LIKE :query ORDER BY created_at DESC"
    )
    fun searchRecords(query: String): Flow<List<SkuRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SkuRecord): Long

    @Update
    suspend fun update(record: SkuRecord)

    @Delete
    suspend fun delete(record: SkuRecord)
}
