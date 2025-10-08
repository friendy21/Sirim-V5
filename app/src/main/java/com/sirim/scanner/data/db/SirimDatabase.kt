package com.sirim.scanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SirimRecord::class, SkuRecord::class],
    version = 4,
    exportSchema = true
)
abstract class SirimDatabase : RoomDatabase() {
    abstract fun sirimRecordDao(): SirimRecordDao
    abstract fun skuRecordDao(): SkuRecordDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("BEGIN TRANSACTION")
                try {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS sku_records (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "barcode TEXT NOT NULL, " +
                            "batch_no TEXT, " +
                            "brand_trademark TEXT, " +
                            "model TEXT, " +
                            "type TEXT, " +
                            "rating TEXT, " +
                            "size TEXT, " +
                            "image_path TEXT, " +
                            "created_at INTEGER NOT NULL, " +
                            "is_verified INTEGER NOT NULL, " +
                            "needs_sync INTEGER NOT NULL, " +
                            "server_id TEXT, " +
                            "last_synced INTEGER)"
                    )
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sku_records_barcode ON sku_records(barcode)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sku_records_created_at ON sku_records(created_at)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sku_records_brand_trademark ON sku_records(brand_trademark)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sku_records_is_verified ON sku_records(is_verified)")
                    database.execSQL("COMMIT")
                } catch (error: Exception) {
                    database.execSQL("ROLLBACK")
                    throw error
                }
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sirim_records_brand_verified " +
                        "ON sirim_records(brand_trademark, is_verified)"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sirim_records ADD COLUMN custom_fields TEXT")
                database.execSQL("ALTER TABLE sirim_records ADD COLUMN capture_confidence REAL")
            }
        }
    }
}
