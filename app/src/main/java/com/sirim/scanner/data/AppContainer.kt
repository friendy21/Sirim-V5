package com.sirim.scanner.data

import android.content.Context
import androidx.room.Room
import com.sirim.scanner.data.db.SirimDatabase
import com.sirim.scanner.data.export.ExportManager
import com.sirim.scanner.data.ocr.BarcodeAnalyzer
import com.sirim.scanner.data.ocr.LabelAnalyzer
import com.sirim.scanner.data.preferences.PreferencesManager
import com.sirim.scanner.data.repository.SirimRepository
import com.sirim.scanner.data.repository.SirimRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val repository: SirimRepository
    val exportManager: ExportManager
    val labelAnalyzer: LabelAnalyzer
    val barcodeAnalyzer: BarcodeAnalyzer
    val applicationScope: CoroutineScope
    val preferencesManager: PreferencesManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val applicationScopeImpl = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: SirimDatabase = Room.databaseBuilder(
        context,
        SirimDatabase::class.java,
        "sirim_records.db"
    ).addMigrations(
        SirimDatabase.MIGRATION_1_2,
        SirimDatabase.MIGRATION_2_3,
        SirimDatabase.MIGRATION_3_4
    ).build()

    override val repository: SirimRepository by lazy {
        SirimRepositoryImpl(
            sirimDao = database.sirimRecordDao(),
            skuDao = database.skuRecordDao(),
            context = context.applicationContext
        )
    }

    override val exportManager: ExportManager by lazy {
        ExportManager(context.applicationContext)
    }

    // ✅ You removed Tesseract—so provide the ML Kit–only analyzer here.
    override val labelAnalyzer: LabelAnalyzer by lazy { LabelAnalyzer() }

    override val barcodeAnalyzer: BarcodeAnalyzer by lazy { BarcodeAnalyzer() }

    override val applicationScope: CoroutineScope
        get() = applicationScopeImpl

    override val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context.applicationContext)
    }
}
