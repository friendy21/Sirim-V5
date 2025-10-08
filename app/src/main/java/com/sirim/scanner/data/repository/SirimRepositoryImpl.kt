package com.sirim.scanner.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.sirim.scanner.data.db.SirimRecord
import com.sirim.scanner.data.db.SirimRecordDao
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.db.SkuRecordDao
import com.sirim.scanner.data.db.StorageRecord
import com.sirim.scanner.data.db.asStorageRecords
import com.sirim.scanner.data.db.asStorageRecordsFromSku // <-- IMPORTANT: Make sure this import is present!
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SirimRepositoryImpl(
    private val sirimDao: SirimRecordDao,
    private val skuDao: SkuRecordDao,
    private val context: Context
) : SirimRepository {

    private val fileMutex = Mutex()

    override val sirimRecords: Flow<List<SirimRecord>> = sirimDao.getAllRecords()

    override val skuRecords: Flow<List<SkuRecord>> = skuDao.getAllRecords()

    override val storageRecords: Flow<List<StorageRecord>> = combine(sirimRecords, skuRecords) { sirim, sku ->
        // THIS IS THE FIX: Use the correct function name for the sku list
        (sirim.asStorageRecords() + sku.asStorageRecordsFromSku()).sortedByDescending { it.createdAt }
    }

    override fun searchSirim(query: String): Flow<List<SirimRecord>> = sirimDao.searchRecords("%$query%")

    override fun searchSku(query: String): Flow<List<SkuRecord>> = skuDao.searchRecords("%$query%")

    override fun searchAll(query: String): Flow<List<StorageRecord>> =
        combine(searchSirim(query), searchSku(query)) { sirim, sku ->
            // APPLY THE SAME FIX HERE
            (sirim.asStorageRecords() + sku.asStorageRecordsFromSku()).sortedByDescending { it.createdAt }
        }

    override suspend fun upsert(record: SirimRecord): Long = withContext(Dispatchers.IO) {
        try {
            sirimDao.upsert(record)
        } catch (error: SQLiteConstraintException) {
            throw DuplicateRecordException("Serial ${record.sirimSerialNo} already exists")
        } catch (error: Exception) {
            throw DatabaseException("Failed to save record", error)
        }
    }

    override suspend fun upsertSku(record: SkuRecord): Long = skuDao.upsert(record)

    override suspend fun delete(record: SirimRecord) {
        record.imagePath?.let { path ->
            runCatching { File(path).takeIf(File::exists)?.delete() }
        }
        sirimDao.delete(record)
    }

    override suspend fun deleteSku(record: SkuRecord) {
        record.imagePath?.let { path ->
            runCatching { File(path).takeIf(File::exists)?.delete() }
        }
        skuDao.delete(record)
    }

    override suspend fun getRecord(id: Long): SirimRecord? = sirimDao.getRecordById(id)

    override suspend fun getSkuRecord(id: Long): SkuRecord? = skuDao.getRecordById(id)

    override suspend fun findBySerial(serial: String): SirimRecord? = sirimDao.findBySerial(serial)

    override suspend fun findByBarcode(barcode: String): SkuRecord? = skuDao.findByBarcode(barcode)

    override suspend fun persistImage(bytes: ByteArray, extension: String): String {
        val directory = File(context.filesDir, "captured")
        if (!directory.exists()) directory.mkdirs()
        return fileMutex.withLock {
            val file = File(directory, "sirim_${System.currentTimeMillis()}.$extension")
            FileOutputStream(file).use { output ->
                output.write(bytes)
            }
            file.absolutePath
        }
    }
}
