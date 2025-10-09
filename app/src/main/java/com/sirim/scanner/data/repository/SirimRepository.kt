package com.sirim.scanner.data.repository

import com.sirim.scanner.data.db.SirimRecord
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.StorageRecord
import kotlinx.coroutines.flow.Flow

interface SirimRepository {
    val sirimRecords: Flow<List<SirimRecord>>
    val skuRecords: Flow<List<SkuRecord>>
    val storageRecords: Flow<List<StorageRecord>>
    val skuExports: Flow<List<SkuExportRecord>>

    val records: Flow<List<SirimRecord>>
        get() = sirimRecords

    fun searchSirim(query: String): Flow<List<SirimRecord>>
    fun searchSku(query: String): Flow<List<SkuRecord>>
    fun searchAll(query: String): Flow<List<StorageRecord>>

    fun search(query: String): Flow<List<SirimRecord>> = searchSirim(query)

    suspend fun upsert(record: SirimRecord): Long
    suspend fun upsertSku(record: SkuRecord): Long

    suspend fun delete(record: SirimRecord)
    suspend fun deleteSku(record: SkuRecord)

    suspend fun clearSirim()
    suspend fun deleteSkuExport(record: SkuExportRecord)

    suspend fun getRecord(id: Long): SirimRecord?
    suspend fun getSkuRecord(id: Long): SkuRecord?
    suspend fun getAllSkuRecords(): List<SkuRecord>

    suspend fun findBySerial(serial: String): SirimRecord?
    suspend fun findByBarcode(barcode: String): SkuRecord?

    suspend fun persistImage(bytes: ByteArray, extension: String = "jpg"): String

    suspend fun recordSkuExport(record: SkuExportRecord): Long
}
