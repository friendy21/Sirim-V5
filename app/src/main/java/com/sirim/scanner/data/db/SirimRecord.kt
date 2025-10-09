package com.sirim.scanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sirim_records",
    indices = [
        Index(value = ["sirim_serial_no"], unique = true),
        Index(value = ["created_at"]),
        Index(value = ["brand_trademark"]),
        Index(value = ["is_verified"])
    ]
)

data class SirimRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sirim_serial_no")
    val sirimSerialNo: String,
    @ColumnInfo(name = "batch_no")
    val batchNo: String,
    @ColumnInfo(name = "brand_trademark")
    val brandTrademark: String,
    @ColumnInfo(name = "model")
    val model: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "rating")
    val rating: String,
    @ColumnInfo(name = "size")
    val size: String,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "custom_fields")
    val customFields: String? = null,
    @ColumnInfo(name = "capture_confidence")
    val captureConfidence: Float? = null,
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
