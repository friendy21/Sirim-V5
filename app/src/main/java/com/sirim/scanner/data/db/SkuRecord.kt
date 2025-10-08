package com.sirim.scanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sku_records",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["created_at"]),
        Index(value = ["brand_trademark"]),
        Index(value = ["is_verified"])
    ]
)
data class SkuRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "barcode")
    val barcode: String,
    @ColumnInfo(name = "batch_no")
    val batchNo: String? = null,
    @ColumnInfo(name = "brand_trademark")
    val brandTrademark: String? = null,
    @ColumnInfo(name = "model")
    val model: String? = null,
    @ColumnInfo(name = "type")
    val type: String? = null,
    @ColumnInfo(name = "rating")
    val rating: String? = null,
    @ColumnInfo(name = "size")
    val size: String? = null,
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_verified")
    val isVerified: Boolean = false,
    @ColumnInfo(name = "needs_sync")
    val needsSync: Boolean = true,
    @ColumnInfo(name = "server_id")
    val serverId: String? = null,
    @ColumnInfo(name = "last_synced")
    val lastSynced: Long? = null
)
