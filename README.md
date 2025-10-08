# SIRIM QR Code Scanner MVP Plan

## Overview
- **Target Platform:** Android 13+ (API Level 33+)
- **Purpose:** First prototype for SIRIM QR code recognition operating fully offline.

## Getting Started

The repository now contains a full Android Studio project that implements the MVP described below. The project lives in the `app` module and is built with Kotlin, Jetpack Compose, CameraX, ML Kit, Room, and the export libraries discussed in the plan.

### Prerequisites
- Android Studio Giraffe (or newer) with Android SDK 34 installed.
- A device or emulator running Android 13 (API 33) or later with camera capabilities for end-to-end scanning tests.

### Build & Run
1. Open the project root (`Sirim-v2`) in Android Studio.
2. Sync Gradle when prompted and let the IDE download dependencies (ML Kit models download on first run at build time).
3. Connect an Android 13+ device or start an emulator.
4. Use **Run ▶ Run 'app'** to install and launch the application. Log in with `admin / admin`.

### OCR Fallback Setup
- The ML Kit recogniser handles most captures, but the app also ships with a Tesseract fallback. Place the English trained data file at `app/src/main/assets/tessdata/eng.traineddata` (or push it to the device's `/files/tesseract/tessdata` directory) to enable the fallback path. Without this file the app now surfaces an explicit warning (“Tesseract model missing...”) and gracefully falls back to ML Kit only.

### Testing Notes
- Unit/UI tests are scaffolded via the default Gradle tasks (`./gradlew test`, `./gradlew connectedAndroidTest`). They currently serve as placeholders until bespoke tests are implemented for the MVP.
- OCR and camera functionality should be validated on physical hardware for accurate results.

## Project Structure Highlights

```
app/
 ├── build.gradle.kts           # Android app configuration and dependencies
 ├── src/main/
 │   ├── AndroidManifest.xml    # Declares permissions, provider, and MainActivity
 │   ├── java/com/sirim/scanner
 │   │   ├── data                # Room database, repositories, OCR helpers, export manager
 │   │   ├── ui                  # Compose screens (login, dashboard, scanner, records, export)
 │   │   ├── MainActivity.kt     # Compose navigation host
 │   │   └── SirimScannerApplication.kt
 │   └── res                     # Compose theme values and adaptive launcher icons
 └── proguard-rules.pro
```

The remainder of this document retains the original MVP specification for reference.

## Core Requirements
### Basic Functionality
- QR code / label scanning with advanced OCR.
- Automatic data recognition that fills structured tables.
- Fully offline operation with local data storage.
- Export of captured data to PDF, Excel, and CSV.
- Simple hardcoded authentication (admin / admin).
- Prepared infrastructure for future online sync.

### Data Structure
The application captures the following fields per record:

| Field | Max Length | Description |
| --- | --- | --- |
| SIRIM Serial No. | 12 | No spaces or dashes |
| Batch No. | 200 | Product batch number |
| Brand / Trademark | 1024 | As per license |
| Model | 1500 | As per license |
| Type | 1500 | As per license |
| Rating | 600 | As per license |
| Size | 1500 | Product size |

## Technical Stack
- **Language:** Kotlin
- **Architecture:** MVVM + Clean Architecture
- **UI:** Jetpack Compose
- **Database:** Room
- **Camera:** CameraX
- **OCR:** Google ML Kit Vision (primary), Tesseract (fallback)
- **Image Processing:** OpenCV
- **QR Codes:** ZXing + ML Kit Barcode Scanner
- **Exports:** iText 7 (PDF), Apache POI (Excel), built-in CSV handling
- **Sharing:** Android ShareSheet API

## Application Architecture
```
Login Screen (admin/admin)
    ↓
Main Dashboard
    ↓
Camera Scanner ← → Data Table View
    ↓              ↓
Data Entry     Export Options
```

### Key Screens
- **LoginActivity:** Hardcoded authentication.
- **MainActivity:** Dashboard with scanning entry points and record overview.
- **ScannerActivity:** Camera with real-time OCR pipeline and manual capture.
- **DataEntryActivity:** Manual data entry/editing.
- **ExportActivity:** Export selection and format handling.

## Database Schema (Room)
```kotlin
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
```

## Key Feature Implementation Notes
### Enhanced Camera & OCR
- Auto-adjust lighting, focus, and exposure using CameraX.
- Real-time analysis using ML Kit with an automatic Tesseract fallback (requires the `eng.traineddata` model described above).
- Image preprocessing via OpenCV for denoising, thresholding, and perspective cleanup.
- Manual tap-to-capture option when auto detection is insufficient.

### Batch Scanning
- Toggle batch mode inside the scanner to queue multiple captures without leaving the camera view.
- Review queued serials with per-item confidence summaries before committing.
- Save or clear the queue in bulk; duplicates are automatically skipped during batch persistence.

### Smart Data Extraction
- Pattern recognition for SIRIM labels.
- Automatic field mapping with validation.
- Manual overrides for user corrections.

### Data Management
- Compose-based table view with free-text search, brand/date/verification filters, edit, and delete capabilities.
- Stored images linked to records for reference.

### Export Functionality
- Export filtered record sets (brand, verification state, date range) to PDF, Excel, and CSV.
- Excel exports include multi-sheet output (summary, full data, grouped by brand).
- Generated files are stored under `Android/data/.../SIRIM_Exports/<yyyyMMdd>/` with timestamped filenames and can be shared via the ShareSheet.

## Development Phases
1. **Week 1 – Core Setup:** Project scaffolding, basic UI, Room integration, navigation.
2. **Weeks 2-3 – Camera & OCR:** CameraX, ML Kit, Tesseract, OpenCV preprocessing, data extraction pipeline.
3. **Week 4 – Data Management:** CRUD flows, validation, table/list presentation.
4. **Weeks 5-6 – Export & Polish:** Export formats, sharing, UI polish, device testing.

## Performance & Testing
- Optimize camera frame processing, focus, and memory management.
- Improve OCR accuracy with preprocessing and dual-engine validation.
- Add Room indexing and pagination for large datasets.
- Testing strategy covering unit, UI, camera/OCR, export, and multi-device scenarios.

## Future Online Integration Prep
- Implement repository pattern with Retrofit-ready data layer.
- Track sync state (`needsSync`, `serverId`, `lastSynced`) for future server interactions.

## Success Criteria
- Accurate OCR (>90% for clear labels) within 3 seconds.
- Seamless offline operation and storage efficiency (<10MB per 100 records with images).
- Fast startup (<2 seconds) and exports (<5 seconds for 100 records).

