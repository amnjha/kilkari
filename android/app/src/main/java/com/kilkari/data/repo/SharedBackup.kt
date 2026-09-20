package com.kilkari.data.repo

import com.kilkari.data.db.AlbumEntity
import com.kilkari.data.db.AppointmentEntity
import com.kilkari.data.db.BabyEntity
import com.kilkari.data.db.DoctorEntity
import com.kilkari.data.db.EventEntity
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.InvestmentEntity
import com.kilkari.data.db.KilkariDatabase
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.PaperworkEntity
import com.kilkari.data.db.ReminderEntity
import com.kilkari.data.db.TimelineEntity
import com.kilkari.data.db.VaccineDoseEntity
import com.kilkari.domain.Currency
import com.kilkari.domain.FundTxnKind
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The backup format both apps speak, documented in `common/BACKUP.md`.
 *
 * This app's own backup is a zip around the Room database: complete, photographs and scanned
 * pages included, and restorable only by this app, because the file inside is SQLite laid out
 * the way Room wants it. That is the right thing to keep for a phone-to-phone move on the
 * same platform, and the wrong thing entirely for moving to an iPhone.
 *
 * So there is a second one. Plain JSON, one array per kind of record, readable in a text
 * editor and written identically by both apps. It carries less — no photographs, no scanned
 * pages, and none of the things only this app has, which are listed in [omissions] rather
 * than silently dropped.
 */
object SharedBackup {

    const val FORMAT = "kilkari-backup"
    const val VERSION = 1

    private val stamp: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    /** What this app holds that the shared format has no room for. */
    val omissions = listOf(
        "Photographs and scanned document pages",
        "Medicines and the doses taken of them",
        "Teeth",
        "Savings accounts, transfers and contributions",
    )

    class NotABackup(message: String) : Exception(message)

    /**
     * The three preferences the shared format carries.
     *
     * An interface rather than the settings store itself, so the format can be exercised
     * without DataStore — which is what makes the round trip testable.
     */
    interface Settings {
        suspend fun setCurrency(currency: Currency)
        suspend fun setMetric(metric: Boolean)
        suspend fun setSchedule(id: String)
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    /** What a file turned out to hold, so a parent can be shown it before anything happens. */
    data class Summary(
        val writtenBy: String,
        val babyName: String?,
        val counts: List<Pair<String, Int>>,
    ) {
        val total get() = counts.sumOf { it.second }
    }

    /**
     * Reads far enough to say what is in a file, without touching the database.
     *
     * Nothing is imported until this has been shown and agreed to: a restore replaces
     * everything, and "everything" should be a number the parent has read.
     */
    fun summarise(text: String): Pair<Summary, JSONObject> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw NotABackup("That file is not JSON.")
        }
        if (root.optString("format") != FORMAT) throw NotABackup("That is not a Kilkari backup.")
        val version = root.optInt("version")
        if (version > VERSION) {
            throw NotABackup("That backup is version $version, written by a newer Kilkari than this one.")
        }
        val labels = listOf(
            "logEntries" to "Log entries", "doses" to "Doses given", "growth" to "Measurements",
            "expenses" to "Expenses", "deposits" to "Deposits", "investments" to "Holdings",
            "appointments" to "Appointments", "milestones" to "Moments", "events" to "Dates",
            "albums" to "Albums", "doctors" to "Doctors", "reminders" to "Reminders",
            "paperwork" to "Paperwork",
        )
        val counts = labels.mapNotNull { (key, label) ->
            val length = root.optJSONArray(key)?.length() ?: 0
            if (length == 0) null else label to length
        }
        return Summary(
            writtenBy = root.optString("writtenBy", "unknown"),
            babyName = root.optJSONObject("baby")?.optString("name")?.takeIf { it.isNotBlank() },
            counts = counts,
        ) to root
    }

    /**
     * Replaces everything with what the file holds.
     *
     * A restore, not a merge. Merging two histories of the same baby produces duplicate feeds
     * nobody can tell apart, and the parent asked to put a backup back rather than to add one
     * to what is already here.
     */
    suspend fun restore(root: JSONObject, db: KilkariDatabase, settings: Settings) {
        db.clearAllTables()

        root.optJSONObject("settings")?.let { s ->
            s.optString("currency").takeIf { it.isNotBlank() }
                ?.let { settings.setCurrency(Currency.of(it)) }
            if (s.has("metric")) settings.setMetric(s.getBoolean("metric"))
            s.optString("scheduleId").takeIf { it.isNotBlank() }?.let { settings.setSchedule(it) }
        }

        val scheduleId = root.optJSONObject("settings")?.optString("scheduleId")
            ?.takeIf { it.isNotBlank() } ?: "iap"

        val babyJson = root.optJSONObject("baby") ?: throw NotABackup("That backup has no baby in it.")
        val babyId = db.babyDao().insert(
            BabyEntity(
                name = babyJson.optString("name", "Baby"),
                dob = date(babyJson, "dob") ?: LocalDate.now(),
                // The shared format spells these out; this app stores a single letter.
                sex = when (babyJson.optString("sex")) {
                    "boy" -> "m"
                    "girl" -> "f"
                    else -> null
                },
            )
        )

        root.each("logEntries") { row ->
            db.logDao().insert(
                LogEntryEntity(
                    babyId = babyId,
                    kind = row.optString("kind", "feed"),
                    startAt = dateTime(row, "startAt") ?: LocalDateTime.now(),
                    endAt = dateTime(row, "endAt"),
                    feedType = row.text("feedType"),
                    side = row.text("side"),
                    amount = if (row.isNull("amount")) null else row.optInt("amount"),
                    diaperKind = row.text("diaperKind"),
                    note = row.text("note"),
                )
            )
        }
        root.each("doses") { row ->
            db.vaccineDao().upsertDose(
                VaccineDoseEntity(
                    babyId = babyId,
                    scheduleId = scheduleId,
                    groupLabel = row.optString("group"),
                    vaccineName = row.optString("vaccine"),
                    givenOn = date(row, "givenOn") ?: LocalDate.now(),
                    clinic = row.text("clinic"),
                    brand = row.text("brand"),
                )
            )
        }
        root.each("growth") { row ->
            db.growthDao().upsert(
                GrowthEntity(
                    babyId = babyId,
                    date = date(row, "date") ?: LocalDate.now(),
                    weightKg = row.number("weightKg"),
                    lengthCm = row.number("lengthCm"),
                    headCm = row.number("headCm"),
                )
            )
        }
        root.each("expenses") { row ->
            db.expenseDao().insert(
                ExpenseEntity(
                    babyId = babyId,
                    title = row.optString("title"),
                    vendor = row.text("vendor"),
                    // The shared format spells the category out; this app keys it short.
                    category = if (row.optString("category") == "medical") "med" else "gen",
                    amountInr = row.optLong("amount"),
                    date = date(row, "date") ?: LocalDate.now(),
                )
            )
        }
        root.each("deposits") { row ->
            db.fundDao().insert(
                FundTxnEntity(
                    babyId = babyId,
                    kind = FundTxnKind.DEPOSIT.key,
                    amountInr = row.optLong("amount"),
                    date = date(row, "date") ?: LocalDate.now(),
                    note = row.text("note"),
                )
            )
        }
        root.each("investments") { row ->
            db.investmentDao().insert(
                InvestmentEntity(
                    babyId = babyId,
                    name = row.optString("name"),
                    kind = row.optString("kind", "other"),
                    monthlyInr = if (row.isNull("monthly")) null else row.optLong("monthly"),
                    interestRate = row.number("rate"),
                    startDate = date(row, "startedOn") ?: LocalDate.now(),
                    maturityDate = date(row, "maturesOn"),
                    currentValueInr = if (row.isNull("value")) null else row.optLong("value"),
                    valueAsOf = date(row, "valuedOn"),
                )
            )
        }
        root.each("appointments") { row ->
            db.appointmentDao().insert(
                AppointmentEntity(
                    babyId = babyId,
                    title = row.optString("title"),
                    startAt = dateTime(row, "startAt") ?: LocalDateTime.now(),
                    doctor = row.text("who"),
                )
            )
        }
        root.each("milestones") { row ->
            db.timelineDao().insert(
                TimelineEntity(
                    babyId = babyId,
                    date = date(row, "date") ?: LocalDate.now(),
                    title = row.optString("title"),
                    subtitle = row.text("note").orEmpty(),
                )
            )
        }
        root.each("events") { row ->
            db.eventDao().insert(
                EventEntity(
                    babyId = babyId,
                    title = row.optString("title"),
                    subtitle = row.text("note").orEmpty(),
                    date = date(row, "date") ?: LocalDate.now(),
                    annual = row.optBoolean("annual", true),
                )
            )
        }
        root.each("albums") { row ->
            db.albumDao().insert(
                AlbumEntity(
                    babyId = babyId,
                    title = row.optString("title"),
                    subtitle = row.text("note").orEmpty(),
                    url = row.optString("url"),
                )
            )
        }
        root.each("doctors") { row ->
            db.doctorDao().upsert(
                DoctorEntity(
                    babyId = babyId,
                    name = row.optString("name"),
                    speciality = row.text("speciality"),
                    clinic = row.text("clinic"),
                    phone = row.text("phone"),
                )
            )
        }
        root.each("reminders") { row ->
            val title = row.optString("title")
            db.reminderDao().upsert(
                ReminderEntity(
                    // Custom reminders are keyed by what they say, which is what this app
                    // does for the ones a parent adds.
                    key = "custom:" + title.lowercase().replace(Regex("[^a-z0-9]+"), "_"),
                    title = title,
                    subtitle = "",
                    enabled = row.optBoolean("enabled", true),
                    builtIn = false,
                    minuteOfDay = row.optInt("minuteOfDay"),
                    repeatRule = row.optString("cadence", "daily"),
                    weekday = row.optInt("weekday").takeIf { row.optString("cadence") == "weekly" },
                )
            )
        }
        root.each("paperwork") { row ->
            val settledOn = date(row, "obtainedOn")
            db.paperworkDao().upsert(
                PaperworkEntity(
                    babyId = babyId,
                    key = row.optString("key"),
                    status = when {
                        settledOn != null -> "obtained"
                        row.optBoolean("skipped") -> "skipped"
                        else -> "pending"
                    },
                    settledOn = settledOn,
                )
            )
        }
    }

    // ── Writing ─────────────────────────────────────────────────────────────

    /** The same shape the iOS app writes, so a file from here opens there. */
    suspend fun write(db: KilkariDatabase, currency: Currency, metric: Boolean, scheduleId: String): String {
        val baby = db.babyDao().get() ?: throw NotABackup("There is nothing to back up yet.")
        val id = baby.id

        // Everything is read first and the document built afterwards: the JSON builders below
        // take ordinary lambdas, and a suspending call inside one will not compile.
        val logs = db.logDao().allForExport(id)
        val doses = db.vaccineDao().allForExport(id)
        val growth = db.growthDao().allForExport(id)
        val expenses = db.expenseDao().allForExport(id)
        val deposits = db.fundDao().allForExport(id).filter { it.kind == FundTxnKind.DEPOSIT.key }
        val investments = db.investmentDao().allForExport(id)
        val appointments = db.appointmentDao().observeAll(id).first()
        val milestones = db.timelineDao().allForExport(id)
        val events = db.eventDao().allForExport(id)
        val albums = db.albumDao().observeAll(id).first()
        val doctors = db.doctorDao().allForExport(id)
        val reminders = db.reminderDao().observeAll().first().filterNot { it.builtIn }
        val paperwork = db.paperworkDao().observeAll(id).first()

        fun obj(build: JSONObject.() -> Unit) = JSONObject().apply(build)
        fun <T> rows(items: List<T>, build: JSONObject.(T) -> Unit) = JSONArray().apply {
            items.forEach { item -> put(JSONObject().apply { build(item) }) }
        }

        return obj {
            put("format", FORMAT)
            put("version", VERSION)
            put("writtenBy", "android")
            put("writtenAt", stamp.format(LocalDateTime.now().toInstant(ZoneOffset.UTC)))
            put("settings", obj {
                put("currency", currency.code)
                put("metric", metric)
                put("scheduleId", scheduleId)
            })
            put("baby", obj {
                put("name", baby.name)
                put("dob", iso(baby.dob))
                put("sex", when (baby.sex) {
                    "m" -> "boy"
                    "f" -> "girl"
                    else -> JSONObject.NULL
                })
            })
            put("logEntries", rows(logs) { e ->
                put("kind", e.kind)
                put("startAt", iso(e.startAt))
                put("endAt", e.endAt?.let { iso(it) } ?: JSONObject.NULL)
                put("amount", e.amount ?: JSONObject.NULL)
                put("side", e.side ?: JSONObject.NULL)
                put("feedType", e.feedType ?: JSONObject.NULL)
                put("diaperKind", e.diaperKind ?: JSONObject.NULL)
                put("note", e.note ?: JSONObject.NULL)
            })
            put("doses", rows(doses) { d ->
                put("group", d.groupLabel)
                put("vaccine", d.vaccineName)
                put("givenOn", iso(d.givenOn))
                put("clinic", d.clinic ?: JSONObject.NULL)
                put("brand", d.brand ?: JSONObject.NULL)
            })
            put("growth", rows(growth) { g ->
                put("date", iso(g.date))
                put("weightKg", g.weightKg ?: JSONObject.NULL)
                put("lengthCm", g.lengthCm ?: JSONObject.NULL)
                put("headCm", g.headCm ?: JSONObject.NULL)
            })
            put("expenses", rows(expenses) { e ->
                put("title", e.title)
                put("vendor", e.vendor ?: JSONObject.NULL)
                put("category", if (e.category == "med") "medical" else "general")
                put("amount", e.amountInr)
                put("date", iso(e.date))
                put("paidFromFund", e.fundAccountId != null)
            })
            put("deposits", rows(deposits) { t ->
                put("note", t.note ?: JSONObject.NULL)
                put("amount", t.amountInr)
                put("date", iso(t.date))
            })
            put("investments", rows(investments) { i ->
                put("name", i.name)
                put("kind", i.kind)
                put("invested", 0)
                put("monthly", i.monthlyInr ?: JSONObject.NULL)
                put("rate", i.interestRate ?: JSONObject.NULL)
                put("startedOn", iso(i.startDate))
                put("maturesOn", i.maturityDate?.let { iso(it) } ?: JSONObject.NULL)
                put("value", i.currentValueInr ?: JSONObject.NULL)
                put("valuedOn", i.valueAsOf?.let { iso(it) } ?: JSONObject.NULL)
            })
            put("appointments", rows(appointments) { a ->
                put("title", a.title)
                put("who", a.doctor ?: JSONObject.NULL)
                put("startAt", iso(a.startAt))
            })
            put("milestones", rows(milestones) { t ->
                put("title", t.title)
                put("note", t.subtitle.ifBlank { null } ?: JSONObject.NULL)
                put("date", iso(t.date))
            })
            put("events", rows(events) { e ->
                put("title", e.title)
                put("note", e.subtitle.ifBlank { null } ?: JSONObject.NULL)
                put("date", iso(e.date))
                put("annual", e.annual)
            })
            put("albums", rows(albums) { a ->
                put("title", a.title)
                put("note", a.subtitle.ifBlank { null } ?: JSONObject.NULL)
                put("url", a.url)
            })
            put("doctors", rows(doctors) { d ->
                put("name", d.name)
                put("speciality", d.speciality ?: JSONObject.NULL)
                put("clinic", d.clinic ?: JSONObject.NULL)
                put("phone", d.phone ?: JSONObject.NULL)
            })
            put("reminders", rows(reminders) { r ->
                put("title", r.title)
                put("minuteOfDay", r.minuteOfDay ?: 0)
                put("cadence", if (r.repeatRule == "weekly") "weekly" else "daily")
                put("weekday", r.weekday ?: 1)
                put("enabled", r.enabled)
            })
            put("paperwork", rows(paperwork) { p ->
                put("key", p.key)
                put("obtainedOn", p.settledOn?.let { iso(it) } ?: JSONObject.NULL)
                put("skipped", p.status == "skipped")
            })
        }.toString(2)
    }

    // ── Plumbing ────────────────────────────────────────────────────────────

    private inline fun JSONObject.each(key: String, body: (JSONObject) -> Unit) {
        val array = optJSONArray(key) ?: return
        for (i in 0 until array.length()) array.optJSONObject(i)?.let(body)
    }

    /** A string field, treating both a JSON null and an empty string as absent. */
    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.number(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun iso(date: LocalDate): String = stamp.format(date.atStartOfDay().toInstant(ZoneOffset.UTC))

    private fun iso(at: LocalDateTime): String = stamp.format(at.toInstant(ZoneOffset.UTC))

    /**
     * Dates arrive as ISO instants in UTC and are read back as local dates.
     *
     * Both apps write the instant a local date began in UTC, so reading it back in UTC is
     * what returns the day that was meant. Parsing it in the phone's own zone would move a
     * birthday by a day for anyone west of Greenwich.
     */
    private fun date(row: JSONObject, key: String): LocalDate? =
        dateTime(row, key)?.toLocalDate()

    private fun dateTime(row: JSONObject, key: String): LocalDateTime? {
        val text = row.text(key) ?: return null
        return runCatching {
            LocalDateTime.ofInstant(java.time.Instant.parse(text), ZoneOffset.UTC)
        }.getOrNull()
    }
}
