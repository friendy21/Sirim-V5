package com.sirim.scanner.data.db

// No change needed here. Your sealed class design is good.
sealed class StorageRecord {
    abstract val id: Long
    abstract val createdAt: Long

    data class Sirim(val record: SirimRecord) : StorageRecord() {
        override val id: Long = record.id
        override val createdAt: Long = record.createdAt
    }

    data class Sku(val record: SkuRecord) : StorageRecord() {
        override val id: Long = record.id
        override val createdAt: Long = record.createdAt
    }
}

// THIS IS THE FIX:
// This function now correctly converts a List of SirimRecord objects
// into a List of StorageRecord.Sirim objects.
fun List<SirimRecord>.asStorageRecords(): List<StorageRecord> {
    // For each 'SirimRecord' in the list, wrap it in a 'StorageRecord.Sirim'
    return map { record -> StorageRecord.Sirim(record) }
}

// THIS IS THE SECOND FIX:
// This function correctly converts a List of SkuRecord objects
// into a List of StorageRecord.Sku objects.
fun List<SkuRecord>.asStorageRecordsFromSku(): List<StorageRecord> {
    // For each 'SkuRecord' in the list, wrap it in a 'StorageRecord.Sku'
    return map { record -> StorageRecord.Sku(record) }
}
