package com.kilkari.domain

import java.time.LocalDate

/**
 * One of the identity documents a child needs, in the order they are applied for.
 *
 * Each one asks for the one before it — Aadhaar enrolment wants the birth certificate, the
 * passport wants both — so they are a chain rather than a list: only one is ever "next", and
 * it becomes due a set number of days after the previous one was settled.
 */
data class PaperworkKind(
    val key: String,
    val title: String,
    val icon: String,
    /** One line on what it is for, shown under the title. */
    val why: String,
    /** What to carry to the counter. */
    val needs: List<String>,
    /** How long after the previous document (or the birth, for the first) it is due. */
    val leadDays: Long,
    /** The same, in words: "45 days from birth". */
    val leadText: String,
    /** Words that, found in a filed scan's title, mean the scan is of this document. */
    private val matchWords: List<String>,
) {
    private val matchers = matchWords.map { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

    /** Whether a scan filed under [documentTitle] is a copy of this document. */
    fun matches(documentTitle: String): Boolean = matchers.any { it.containsMatchIn(documentTitle) }
}

/** Where one document stands. Only one step is ever [ACTIVE]; the rest wait their turn. */
enum class PaperworkStatus(val key: String) {
    /** Still to get, but not yet next in line. */
    WAITING("pending"),
    /** Next in line: the one being reminded about. */
    ACTIVE("pending"),
    OBTAINED("obtained"),
    /** Set aside for now; the chain moves on past it. */
    SKIPPED("skipped");

    companion object {
        /** The stored form only distinguishes obtained, skipped and pending. */
        fun stored(key: String?): PaperworkStatus = when (key) {
            OBTAINED.key -> OBTAINED
            SKIPPED.key -> SKIPPED
            else -> WAITING
        }
    }
}

/** What has been recorded against one document, as the database keeps it. */
data class PaperworkRecord(
    val key: String,
    /** pending | obtained | skipped */
    val status: String = "pending",
    /** The day it was obtained, or set aside — either way the day the chain moved on. */
    val settledOn: LocalDate? = null,
    /** A date of the parent's own, overriding the suggested one. */
    val targetDate: LocalDate? = null,
    val note: String = "",
    /** The filed scan of it, where there is one. */
    val documentId: Long? = null,
)

/** The little a scan needs to say to be matched to a document. */
data class FiledDocument(val id: Long, val title: String, val filedOn: LocalDate)

/** One document, worked out against today. */
data class PaperworkStep(
    val kind: PaperworkKind,
    val index: Int,
    val status: PaperworkStatus,
    /** When it is due: the parent's own date, else the suggested one. Null while waiting. */
    val dueDate: LocalDate?,
    /** Days until [dueDate]; negative once it is late. */
    val inDays: Int?,
    /** The suggested date — null until the previous document is settled. */
    val suggestedDate: LocalDate?,
    /** When it was obtained, or set aside. */
    val settledOn: LocalDate?,
    val note: String,
    val documentId: Long?,
    val documentTitle: String?,
) {
    val overdue: Boolean get() = inDays != null && inDays < 0

    /** True when the parent has set a date of their own rather than taking the suggestion. */
    val customDate: Boolean get() = dueDate != null && dueDate != suggestedDate

    /** The document this one comes after, whose settling starts the clock. */
    val previous: PaperworkKind? get() = Paperwork.KINDS.getOrNull(index - 1)
}

/**
 * The four documents, and how far along they are.
 *
 * The birth certificate is due a fixed number of days from birth; each of the others a fixed
 * number of days after the one before it was settled — obtained or set aside. Nothing is due
 * until its turn comes, so a parent is asked for one thing at a time, in the order the
 * offices themselves insist on.
 */
object Paperwork {

    /** Days from birth to have the birth certificate by. */
    const val BIRTH_CERTIFICATE_DAYS = 45L

    val KINDS: List<PaperworkKind> = listOf(
        PaperworkKind(
            key = "birth_cert",
            title = "Birth certificate",
            icon = "article",
            why = "Registers the birth with the municipality. Everything that follows asks for it.",
            needs = listOf(
                "The hospital's discharge summary or birth letter",
                "Both parents' ID and address proof",
                "Registration within 21 days is free; after that a late fee, and past a year an affidavit",
            ),
            leadDays = BIRTH_CERTIFICATE_DAYS,
            leadText = "45 days from birth",
            matchWords = listOf("birth certificate", "birth cert", "birth registration"),
        ),
        PaperworkKind(
            key = "aadhaar",
            title = "Aadhaar",
            icon = "fingerprint",
            why = "Baal Aadhaar, linked to a parent's. No fingerprints until the child is five.",
            needs = listOf(
                "Birth certificate",
                "One parent's Aadhaar",
                "The child, for a photograph",
            ),
            leadDays = 30,
            leadText = "30 days after the birth certificate",
            matchWords = listOf("aadhaar", "aadhar", "adhaar", "adhar", "baal aadhaar"),
        ),
        PaperworkKind(
            key = "passport",
            title = "Passport",
            icon = "flight",
            why = "Applied for at a Passport Seva Kendra. A minor's passport runs five years.",
            needs = listOf(
                "Birth certificate",
                "Both parents' passports or Aadhaar",
                "Address proof in a parent's name",
                "Annexure D, signed by both parents",
            ),
            leadDays = 60,
            leadText = "60 days after Aadhaar",
            matchWords = listOf("passport"),
        ),
        PaperworkKind(
            key = "pan",
            title = "PAN card",
            icon = "credit_card",
            why = "Needed once anything is invested in the child's name. A parent applies as the representative.",
            needs = listOf(
                "Birth certificate, as proof of date of birth",
                "Aadhaar, as proof of identity",
                "The applying parent's PAN",
            ),
            leadDays = 30,
            leadText = "30 days after the passport",
            matchWords = listOf("pan", "pan card", "permanent account number"),
        ),
    )

    fun byKey(key: String): PaperworkKind? = KINDS.firstOrNull { it.key == key }

    /** The document a scan filed under [title] is of, if any. */
    fun matching(title: String): PaperworkKind? = KINDS.firstOrNull { it.matches(title) }

    /**
     * Works out where every document stands on [today].
     *
     * Walks the chain in order carrying the date it last moved on: the birth to begin with,
     * then each settled document's date. The first one still pending is the active step and
     * gets a due date; those after it wait, unless the parent has given one a date of their
     * own, in which case that date is shown but it is still not the one being chased.
     */
    fun plan(
        dob: LocalDate,
        records: List<PaperworkRecord>,
        documents: List<FiledDocument>,
        today: LocalDate,
    ): List<PaperworkStep> {
        val byKey = records.associateBy { it.key }
        var clockStart: LocalDate = dob
        var activeSeen = false

        return KINDS.mapIndexed { index, kind ->
            val record = byKey[kind.key]
            val document = record?.documentId?.let { id -> documents.firstOrNull { it.id == id } }
                ?: documents.firstOrNull { kind.matches(it.title) }
            val stored = PaperworkStatus.stored(record?.status)
            val suggested = clockStart.plusDays(kind.leadDays)

            when (stored) {
                PaperworkStatus.OBTAINED, PaperworkStatus.SKIPPED -> {
                    val settled = record?.settledOn ?: document?.filedOn ?: today
                    clockStart = settled
                    PaperworkStep(
                        kind = kind, index = index, status = stored,
                        dueDate = null, inDays = null, suggestedDate = suggested,
                        settledOn = settled, note = record?.note.orEmpty(),
                        documentId = document?.id, documentTitle = document?.title,
                    )
                }

                else -> {
                    val active = !activeSeen
                    activeSeen = true
                    val due = record?.targetDate ?: suggested.takeIf { active }
                    PaperworkStep(
                        kind = kind, index = index,
                        status = if (active) PaperworkStatus.ACTIVE else PaperworkStatus.WAITING,
                        dueDate = due,
                        inDays = due?.let { Fmt.daysUntil(it, today) },
                        suggestedDate = suggested.takeIf { active },
                        settledOn = null, note = record?.note.orEmpty(),
                        documentId = document?.id, documentTitle = document?.title,
                    )
                }
            }
        }
    }
}
