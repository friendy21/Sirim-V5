package com.sirim.scanner.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import com.sirim.scanner.data.db.SirimRecord
import com.sirim.scanner.data.db.SirimRecordDao
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.db.SkuRecordDao
import com.sirim.scanner.data.db.SkuExportDao
import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.StorageRecord
import com.sirim.scanner.data.db.toGalleryList
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
    private val skuExportDao: SkuExportDao,
    private val context: Context
) : SirimRepository {

    private val fileMutex = Mutex()

    override val sirimRecords: Flow<List<SirimRecord>> = sirimDao.getAllRecords()

    override val skuRecords: Flow<List<SkuRecord>> = skuDao.getAllRecords()

    override val skuExports: Flow<List<SkuExportRecord>> = skuExportDao.observeExports()

    override val storageRecords: Flow<List<StorageRecord>> = combine(sirimRecords, skuExports) { sirim, exports ->
        val storageItems = mutableListOf<StorageRecord>()
        val lastUpdated = sirim.maxOfOrNull { it.createdAt } ?: 0L
        storageItems += StorageRecord.SirimDatabase(
            totalRecords = sirim.size,
            lastUpdated = lastUpdated
        )
        storageItems += exports.map { StorageRecord.SkuExport(it) }
        storageItems.sortedByDescending { it.createdAt }
    }

    override fun searchSirim(query: String): Flow<List<SirimRecord>> = sirimDao.searchRecords("%$query%")

    override fun searchSku(query: String): Flow<List<SkuRecord>> = skuDao.searchRecords("%$query%")

    override fun searchAll(query: String): Flow<List<StorageRecord>> = storageRecords

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
        record.galleryPaths.toGalleryList().forEach { path ->
            runCatching { File(path).takeIf(File::exists)?.delete() }
        }
        skuDao.delete(record)
    }

    override suspend fun clearSirim() = withContext(Dispatchers.IO) {
        val records = sirimDao.getAllRecordsOnce()
        records.forEach { record ->
            record.imagePath?.let { path ->
                runCatching { File(path).takeIf(File::exists)?.delete() }
            }
        }
        sirimDao.clearAll()
    }

    override suspend fun deleteSkuExport(record: SkuExportRecord) = withContext(Dispatchers.IO) {
        deleteSkuExportFile(record)
        skuExportDao.delete(record)
    }

    override suspend fun getRecord(id: Long): SirimRecord? = sirimDao.getRecordById(id)

    override suspend fun getSkuRecord(id: Long): SkuRecord? = skuDao.getRecordById(id)

    override suspend fun getAllSkuRecords(): List<SkuRecord> = skuDao.getAllRecordsOnce()

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

    override suspend fun recordSkuExport(record: SkuExportRecord): Long = skuExportDao.upsert(record)

    private fun deleteSkuExportFile(record: SkuExportRecord) {
        val uri = Uri.parse(record.uri)
        val file = when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                val segments = uri.pathSegments
                if (segments.isEmpty()) {
                    null
                } else {
                    val relativePath = segments.drop(1).joinToString(File.separator)
                    val baseDir = context.getExternalFilesDir(null)
                    if (baseDir != null && relativePath.isNotEmpty()) {
                        File(baseDir, relativePath)
                    } else {
                        null
                    }
                }
            }

            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)

            else -> uri.path?.let(::File) ?: File(record.uri)
        }

        file?.takeIf(File::exists)?.let { runCatching { it.delete() } }
    }
}
