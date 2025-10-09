package com.sirim.scanner.data.db

sealed class StorageRecord {
    abstract val id: Long
    abstract val createdAt: Long
    abstract val title: String
    abstract val description: String

    data class SirimDatabase(
        val totalRecords: Int,
        val lastUpdated: Long
    ) : StorageRecord() {
        override val id: Long = 1
        override val createdAt: Long = lastUpdated
        override val title: String = "SIRIM Records"
        override val description: String = if (totalRecords == 1) {
            "1 record stored"
        } else {
            "$totalRecords records stored"
        }
    }

    data class SkuExport(
        val export: SkuExportRecord
    ) : StorageRecord() {
        override val id: Long = export.id + 10_000
        override val createdAt: Long = export.updatedAt
        override val title: String = export.fileName
        override val description: String = "${export.recordCount} SKU captures"
    }
}
