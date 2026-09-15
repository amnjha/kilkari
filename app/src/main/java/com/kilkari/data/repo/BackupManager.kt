package com.kilkari.data.repo

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.kilkari.data.db.KilkariDatabase
import com.kilkari.domain.Currency
import com.kilkari.domain.Fmt
import com.kilkari.domain.VaccineGroupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local-only backup, CSV export and a printable vaccination record.
 *
 * A backup is a plain zip holding the SQLite database plus every scanned page, written through
 * the Storage Access Framework so the user picks where it lands — Drive, Files, anywhere.
 */
object BackupManager {

    private const val DB_ENTRY = "kilkari.db"
    private const val DOCS_PREFIX = "documents/"

    /** Checkpoints WAL into the main database file so the copy is complete. */
    private suspend fun checkpoint(context: Context) = withContext(Dispatchers.IO) {
        KilkariDatabase.get(context).openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    suspend fun writeBackup(context: Context, target: OutputStream): Long = withContext(Dispatchers.IO) {
        checkpoint(context)
        var bytes = 0L
        ZipOutputStream(target.buffered()).use { zip ->
            context.getDatabasePath(KilkariDatabase.DB_NAME).takeIf { it.exists() }?.let { db ->
                zip.putNextEntry(ZipEntry(DB_ENTRY))
                bytes += db.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            File(context.filesDir, "documents").listFiles()?.forEach { page ->
                zip.putNextEntry(ZipEntry(DOCS_PREFIX + page.name))
                bytes += page.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        bytes
    }

    /**
     * Replaces the current database and scans with the contents of a backup. The caller must
     * restart the process afterwards — Room holds the old file open until then.
     */
    suspend fun restoreBackup(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source) ?: return@withContext false
        KilkariDatabase.close()
        val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
        var sawDatabase = false

        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == DB_ENTRY -> {
                        context.getDatabasePath(KilkariDatabase.DB_NAME).also { it.parentFile?.mkdirs() }
                            .outputStream().use { zip.copyTo(it) }
                        // Stale WAL/SHM would shadow the restored file.
                        File(context.getDatabasePath(KilkariDatabase.DB_NAME).path + "-wal").delete()
                        File(context.getDatabasePath(KilkariDatabase.DB_NAME).path + "-shm").delete()
                        sawDatabase = true
                    }
                    name.startsWith(DOCS_PREFIX) && !entry.isDirectory -> {
                        val safeName = File(name).name
                        File(docsDir, safeName).outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        sawDatabase
    }

    /** Everything as one CSV, section by section, so a spreadsheet can open it directly. */
    suspend fun writeCsv(context: Context, target: OutputStream, currency: Currency) =
        withContext(Dispatchers.IO) {
            val db = KilkariDatabase.get(context)
            val baby = db.babyDao().get()
            target.bufferedWriter().use { out ->
                fun row(vararg cells: Any?) {
                    out.write(cells.joinToString(",") { cell ->
                        val text = cell?.toString().orEmpty()
                        if (text.any { it == ',' || it == '"' || it == '\n' }) {
                            "\"" + text.replace("\"", "\"\"") + "\""
                        } else text
                    })
                    out.newLine()
                }

                row("Kilkari export", Fmt.dateFull(java.time.LocalDate.now()))
                baby?.let { row("Baby", it.name, "DOB", it.dob.toString()) }
                out.newLine()

                val id = baby?.id ?: return@use

                row("LOGS")
                row("kind", "startAt", "endAt", "feedType", "side", "amount", "diaper", "place", "medicine", "dose", "note")
                db.logDao().allForExport(id).forEach { e ->
                    row(e.kind, e.startAt, e.endAt, e.feedType, e.side, e.amount, e.diaperKind, e.place, e.medicationName, e.dose, e.note)
                }
                out.newLine()

                row("GROWTH")
                row("date", "weightKg", "lengthCm", "headCm")
                db.growthDao().allForExport(id).forEach { g -> row(g.date, g.weightKg, g.lengthCm, g.headCm) }
                out.newLine()

                row("VACCINES")
                row("schedule", "group", "vaccine", "brand", "givenOn", "clinic")
                db.vaccineDao().allForExport(id).forEach { v ->
                    row(v.scheduleId, v.groupLabel, v.vaccineName, v.brand, v.givenOn, v.clinic)
                }
                out.newLine()

                row("EXPENSES")
                row("date", "title", "vendor", "category", "amountInr", "amount${currency.code}", "paidFromFund")
                db.expenseDao().allForExport(id).forEach { x ->
                    row(x.date, x.title, x.vendor, x.category, x.amountInr, Fmt.money(x.amountInr, currency), x.paidFromFund)
                }
                out.newLine()

                row("TIMELINE")
                row("date", "title", "subtitle", "album")
                db.timelineDao().allForExport(id).forEach { t -> row(t.date, t.title, t.subtitle, t.albumUrl) }
                out.newLine()

                row("EVENTS")
                row("date", "title", "subtitle", "annual")
                db.eventDao().allForExport(id).forEach { e -> row(e.date, e.title, e.subtitle, e.annual) }
                out.newLine()

                row("FUND")
                row("date", "kind", "amountInr", "note")
                db.fundDao().allForExport(id).forEach { f -> row(f.date, f.kind, f.amountInr, f.note) }
                out.newLine()

                row("INVESTMENTS")
                row("name", "kind", "institution", "monthlyInr", "rate", "start", "maturity", "currentValueInr", "maturityValueInr", "active")
                val investments = db.investmentDao().allForExport(id)
                investments.forEach { v ->
                    row(v.name, v.kind, v.institution, v.monthlyInr, v.interestRate, v.startDate, v.maturityDate, v.currentValueInr, v.maturityValueInr, v.active)
                }
                out.newLine()

                row("INVESTMENT CONTRIBUTIONS")
                row("investment", "date", "amountInr", "paidFromFund")
                val nameById = investments.associate { it.id to it.name }
                db.investmentDao().contributionsForExport(id).forEach { c ->
                    row(nameById[c.investmentId], c.date, c.amountInr, c.paidFromFund)
                }
            }
        }

    /** A one-page A4 immunisation record to hand to a school or clinic. */
    suspend fun writeVaccinationPdf(
        context: Context,
        target: OutputStream,
        babyName: String,
        dob: java.time.LocalDate,
        scheduleName: String,
        groups: List<VaccineGroupState>,
    ) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas

        val title = Paint().apply { textSize = 22f; isFakeBoldText = true; color = 0xFF1E1B4B.toInt() }
        val heading = Paint().apply { textSize = 12f; isFakeBoldText = true; color = 0xFF4F46E5.toInt() }
        val body = Paint().apply { textSize = 11f; color = 0xFF1E1B4B.toInt() }
        val muted = Paint().apply { textSize = 10f; color = 0xFF6B7280.toInt() }
        val rule = Paint().apply { color = 0xFFECE9FB.toInt(); strokeWidth = 1f }

        var y = 56f
        canvas.drawText("Immunisation record", 40f, y, title)
        y += 22f
        canvas.drawText("$babyName · born ${Fmt.dateFull(dob)}", 40f, y, body)
        y += 14f
        canvas.drawText("Schedule: $scheduleName", 40f, y, muted)
        y += 10f
        canvas.drawLine(40f, y, 555f, y, rule)
        y += 22f

        groups.forEach { group ->
            if (y > 780f) return@forEach
            canvas.drawText("${group.label} — due ${Fmt.dateFull(group.dueDate)}", 40f, y, heading)
            y += 16f
            group.items.forEach { item ->
                if (y > 800f) return@forEach
                canvas.drawText(if (item.given) "[x]" else "[ ]", 48f, y, body)
                canvas.drawText(item.name, 74f, y, body)
                // Once given, the brand and date are what a clinic actually needs to see.
                val detail = if (item.given) {
                    listOfNotNull(item.brand, item.givenOn?.let(Fmt::dateFull)).joinToString(" · ")
                } else {
                    item.desc
                }
                if (detail.isNotBlank()) canvas.drawText(detail, 250f, y, muted)
                y += 14f
            }
            y += 8f
        }

        canvas.drawText(
            "Generated by Kilkari on ${Fmt.dateFull(java.time.LocalDate.now())}",
            40f, 820f, muted,
        )

        doc.finishPage(page)
        doc.writeTo(target)
        doc.close()
    }
}
