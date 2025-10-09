package com.sirim.scanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SirimRecordDao {
    @Query("SELECT * FROM sirim_records ORDER BY created_at DESC")
    fun getAllRecords(): Flow<List<SirimRecord>>

    @Query("SELECT * FROM sirim_records WHERE id = :id")
    suspend fun getRecordById(id: Long): SirimRecord?

    @Query("SELECT * FROM sirim_records WHERE sirim_serial_no = :serial LIMIT 1")
    suspend fun findBySerial(serial: String): SirimRecord?

    @Query(
        "SELECT * FROM sirim_records WHERE sirim_serial_no LIKE :query OR batch_no LIKE :query OR brand_trademark LIKE :query OR model LIKE :query ORDER BY created_at DESC"
    )
    fun searchRecords(query: String): Flow<List<SirimRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SirimRecord): Long

    @Update
    suspend fun update(record: SirimRecord)

    @Delete
    suspend fun delete(record: SirimRecord)

    @Query("DELETE FROM sirim_records")
    suspend fun clearAll()
}
