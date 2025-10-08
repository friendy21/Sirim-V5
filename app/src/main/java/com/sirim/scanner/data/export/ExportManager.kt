package com.sirim.scanner.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.sirim.scanner.data.db.SirimRecord
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class ExportManager(private val context: Context) {

    private val headers = listOf(
        "SIRIM Serial No.",
        "Batch No.",
        "Brand/Trademark",
        "Model",
        "Type",
        "Rating",
        "Size"
    )

    fun exportToPdf(records: List<SirimRecord>): Uri {
        val file = createExportFile("pdf")
        PdfWriter(FileOutputStream(file)).use { writer ->
            val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
            val document = Document(pdfDoc)

            document.add(Paragraph("SIRIM Records"))

            // iText 7/8 requires FloatArray for column widths
            val columnWidths = FloatArray(headers.size) { 1f }
            val table = Table(columnWidths)

            headers.forEach { header ->
                table.addHeaderCell(Cell().add(Paragraph(header)))
            }

            records.forEach { record ->
                record.toFieldList().forEach { value ->
                    table.addCell(Cell().add(Paragraph(value)))
                }
            }

            document.add(table)
            document.close()
        }
        return toFileUri(file)
    }

    fun exportToCsv(records: List<SirimRecord>): Uri {
        val file = createExportFile("csv")
        FileOutputStream(file).use { output ->
            OutputStreamWriter(output).use { writer ->
                writer.appendLine(headers.joinToString(","))
                records.forEach { record ->
                    writer.appendLine(record.toFieldList().joinToString(","))
                }
            }
        }
        return toFileUri(file)
    }

    fun exportToExcel(records: List<SirimRecord>): Uri {
        val file = createExportFile("xlsx")
        XSSFWorkbook().use { workbook ->
            createSummarySheet(workbook.createSheet("Summary"), records)
            createDataSheet(workbook.createSheet("All Records"), records)
            createBrandSheet(workbook.createSheet("By Brand"), records)

            FileOutputStream(file).use { output ->
                workbook.write(output)
            }
        }
        return toFileUri(file)
    }

    fun exportByDateRange(
        records: List<SirimRecord>,
        startDate: Long?,
        endDate: Long?,
        format: ExportFormat
    ): Uri {
        val filtered = records.filter { record ->
            val createdAt = record.createdAt
            val afterStart = startDate?.let { createdAt >= it } ?: true
            val beforeEnd = endDate?.let { createdAt <= it } ?: true
            afterStart && beforeEnd
        }
        return when (format) {
            ExportFormat.Pdf -> exportToPdf(filtered)
            ExportFormat.Excel -> exportToExcel(filtered)
            ExportFormat.Csv -> exportToCsv(filtered)
        }
    }

    private fun createSummarySheet(sheet: org.apache.poi.ss.usermodel.Sheet, records: List<SirimRecord>) {
        var rowIndex = 0
        val headerRow = sheet.createRow(rowIndex++)
        headerRow.createCell(0).setCellValue("SIRIM Record Summary")

        val totalsRow = sheet.createRow(rowIndex++)
        totalsRow.createCell(0).setCellValue("Total Records")
        totalsRow.createCell(1).setCellValue(records.size.toDouble())

        val verifiedCount = records.count { it.isVerified }
        val verifiedRow = sheet.createRow(rowIndex++)
        verifiedRow.createCell(0).setCellValue("Verified")
        verifiedRow.createCell(1).setCellValue(verifiedCount.toDouble())

        val pendingRow = sheet.createRow(rowIndex++)
        pendingRow.createCell(0).setCellValue("Pending Verification")
        pendingRow.createCell(1).setCellValue((records.size - verifiedCount).toDouble())

        if (records.isNotEmpty()) {
            rowIndex++
            val topBrandsRow = sheet.createRow(rowIndex++)
            topBrandsRow.createCell(0).setCellValue("Top Brands")

            records.groupBy { it.brandTrademark }
                .mapValues { (_, values) -> values.size }
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .forEach { (brand, count) ->
                    val row = sheet.createRow(rowIndex++)
                    row.createCell(0).setCellValue(brand)
                    row.createCell(1).setCellValue(count.toDouble())
                }
        }
    }

    private fun createDataSheet(sheet: org.apache.poi.ss.usermodel.Sheet, records: List<SirimRecord>) {
        val headerStyle: XSSFCellStyle = (sheet.workbook as XSSFWorkbook).createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.MEDIUM
        }
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            sheet.setColumnWidth(index, 20_000)
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }
        records.forEachIndexed { rowIndex, record ->
            val row = sheet.createRow(rowIndex + 1)
            record.toFieldList().forEachIndexed { columnIndex, value ->
                row.createCell(columnIndex).setCellValue(value)
            }
        }
        sheet.setAutoFilter(CellRangeAddress(0, records.size, 0, headers.lastIndex))
    }

    private fun createBrandSheet(sheet: org.apache.poi.ss.usermodel.Sheet, records: List<SirimRecord>) {
        val workbook = sheet.workbook as XSSFWorkbook
        val headerStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.MEDIUM
        }
        val headerRow = sheet.createRow(0)
        listOf("Brand", "Records", "Verified").forEachIndexed { index, title ->
            sheet.setColumnWidth(index, 10_000)
            val cell = headerRow.createCell(index)
            cell.setCellValue(title)
            cell.cellStyle = headerStyle
        }

        records.groupBy { it.brandTrademark }
            .entries
            .sortedByDescending { it.value.size }
            .forEachIndexed { index, entry ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(entry.key)
                row.createCell(1).setCellValue(entry.value.size.toDouble())
                row.createCell(2).setCellValue(entry.value.count { it.isVerified }.toDouble())
            }
    }

    private fun createExportFile(extension: String, prefix: String = "sirim_records"): File {
        val directory = getExportDirectory()
        val timestamp = formatTimestamp(FILE_NAME_FORMAT)
        return File(directory, "${prefix}_$timestamp.$extension")
    }

    private fun getExportDirectory(): File {
        val directoryName = formatTimestamp(DIRECTORY_FORMAT)
        val exportDir = File(context.getExternalFilesDir(null), "SIRIM_Exports/$directoryName")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    private fun toFileUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".provider",
        file
    )

    private fun SirimRecord.toFieldList(): List<String> = listOf(
        sirimSerialNo,
        batchNo,
        brandTrademark,
        model,
        type,
        rating,
        size
    )
}

private val FILE_NAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
private val DIRECTORY_FORMAT = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

private fun formatTimestamp(format: SimpleDateFormat): String = synchronized(format) {
    format.format(Date())
}