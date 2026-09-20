package com.kilkari.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaperworkTest {

    private val dob = LocalDate.of(2026, 8, 1)
    private val today = LocalDate.of(2026, 9, 1)

    private fun plan(
        records: List<PaperworkRecord> = emptyList(),
        documents: List<FiledDocument> = emptyList(),
        on: LocalDate = today,
    ) = Paperwork.plan(dob, records, documents, on)

    private fun obtained(key: String, on: LocalDate) = PaperworkRecord(key, "obtained", settledOn = on)

    @Test
    fun `the birth certificate is due 45 days from birth and is the only active step`() {
        val steps = plan()
        assertEquals(listOf("birth_cert", "aadhaar", "passport", "pan"), steps.map { it.kind.key })
        assertEquals(PaperworkStatus.ACTIVE, steps[0].status)
        assertEquals(dob.plusDays(45), steps[0].dueDate)
        assertEquals(14, steps[0].inDays)
        assertTrue(steps.drop(1).all { it.status == PaperworkStatus.WAITING && it.dueDate == null })
    }

    @Test
    fun `each document becomes due a set time after the one before it was obtained`() {
        val certOn = LocalDate.of(2026, 8, 20)
        val steps = plan(listOf(obtained("birth_cert", certOn)))
        assertEquals(PaperworkStatus.OBTAINED, steps[0].status)
        assertEquals(certOn, steps[0].settledOn)
        assertEquals(PaperworkStatus.ACTIVE, steps[1].status)
        assertEquals(certOn.plusDays(30), steps[1].dueDate)
        assertEquals(PaperworkStatus.WAITING, steps[2].status)
    }

    @Test
    fun `the whole chain runs birth certificate, aadhaar, passport, pan`() {
        val steps = plan(
            listOf(
                obtained("birth_cert", LocalDate.of(2026, 8, 20)),
                obtained("aadhaar", LocalDate.of(2026, 9, 10)),
                obtained("passport", LocalDate.of(2026, 10, 30)),
            ),
            on = LocalDate.of(2026, 11, 1),
        )
        assertEquals(PaperworkStatus.ACTIVE, steps[3].status)
        assertEquals(LocalDate.of(2026, 11, 29), steps[3].dueDate)
        assertEquals(28, steps[3].inDays)
    }

    @Test
    fun `a late document is overdue by the days since it was due`() {
        val steps = plan(on = dob.plusDays(50))
        assertEquals(-5, steps[0].inDays)
        assertTrue(steps[0].overdue)
    }

    @Test
    fun `a parent's own date replaces the suggested one but is still recognisable as their own`() {
        val own = LocalDate.of(2026, 8, 25)
        val steps = plan(listOf(PaperworkRecord("birth_cert", targetDate = own)))
        assertEquals(own, steps[0].dueDate)
        assertEquals(dob.plusDays(45), steps[0].suggestedDate)
        assertTrue(steps[0].customDate)
        assertFalse(plan()[0].customDate)
    }

    @Test
    fun `a waiting step with its own date shows it without becoming the one chased`() {
        val own = LocalDate.of(2026, 12, 1)
        val steps = plan(listOf(PaperworkRecord("passport", targetDate = own)))
        assertEquals(PaperworkStatus.WAITING, steps[2].status)
        assertEquals(own, steps[2].dueDate)
        assertEquals(PaperworkStatus.ACTIVE, steps[0].status)
    }

    @Test
    fun `setting a document aside moves the chain on from the day it was set aside`() {
        val skippedOn = LocalDate.of(2026, 9, 15)
        val steps = plan(
            listOf(
                obtained("birth_cert", LocalDate.of(2026, 8, 20)),
                PaperworkRecord("aadhaar", "skipped", settledOn = skippedOn),
            ),
            on = skippedOn,
        )
        assertEquals(PaperworkStatus.SKIPPED, steps[1].status)
        assertNull(steps[1].dueDate)
        assertEquals(PaperworkStatus.ACTIVE, steps[2].status)
        assertEquals(skippedOn.plusDays(60), steps[2].dueDate)
    }

    @Test
    fun `a filed scan is matched to its document by title, and pan does not match a prescription`() {
        val docs = listOf(
            FiledDocument(1, "Panadol prescription", LocalDate.of(2026, 8, 30)),
            FiledDocument(2, "Aadhar card", LocalDate.of(2026, 8, 28)),
            FiledDocument(3, "Birth Certificate", LocalDate.of(2026, 8, 21)),
        )
        val steps = plan(documents = docs)
        assertEquals(3L, steps[0].documentId)
        assertEquals(2L, steps[1].documentId)
        assertNull(steps[3].documentId)
        assertEquals("pan", Paperwork.matching("PAN card scan")?.key)
        assertNull(Paperwork.matching("Panadol prescription"))
    }

    @Test
    fun `an obtained record without a date falls back to the scan's filing date`() {
        val docs = listOf(FiledDocument(7, "Birth certificate", LocalDate.of(2026, 8, 21)))
        val steps = plan(listOf(PaperworkRecord("birth_cert", "obtained", documentId = 7)), docs)
        assertEquals(LocalDate.of(2026, 8, 21), steps[0].settledOn)
        assertEquals(LocalDate.of(2026, 9, 20), steps[1].dueDate)
    }

    @Test
    fun `the previous document is named for the step that waits on it`() {
        val steps = plan()
        assertNull(steps[0].previous)
        assertEquals("Birth certificate", steps[1].previous?.title)
        assertEquals("Passport", steps[3].previous?.title)
    }
}
