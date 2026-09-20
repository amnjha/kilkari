package com.kilkari.domain

import com.kilkari.data.seed.VaccineSchedules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Keeps the two apps reading the same numbers.
 *
 * `common/data` holds the master copies of the WHO weight-for-age tables and the four
 * vaccination schedules; the iOS app parses those files directly, and this app compiles the
 * same values into Kotlin so it needs no JSON parser on a device for data that never changes
 * at runtime. The risk in that arrangement is drift — one platform edited, the other not —
 * and a schedule that differs by a week between two phones is the kind of bug nobody notices
 * until a parent compares them at a clinic.
 *
 * So the JSON is the master and this test is the proof that the Kotlin still matches it. Edit
 * one without the other and the build goes red here, naming what diverged.
 */
class SharedDataTest {

    // Unit tests run with the module as the working directory, so the shared folder is found
    // by walking up rather than by counting "..", which breaks the next time anything moves.
    private val commonData: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "common/data") }
        .firstOrNull { it.isDirectory }
        ?: throw AssertionError("common/data not found above ${File("").absolutePath}")

    private fun read(name: String) = JSONObject(File(commonData, name).readText())

    @Test
    fun `the compiled schedules are what common data says`() {
        val schedules = read("vaccine-schedules.json").getJSONArray("schedules")
        assertEquals(
            "schedule count",
            schedules.length(),
            VaccineSchedules.all.size,
        )
        for (i in 0 until schedules.length()) {
            val want = schedules.getJSONObject(i)
            val got = VaccineSchedules.all[i]
            assertEquals("schedule $i id", want.getString("id"), got.id)
            assertEquals("${got.id} name", want.getString("name"), got.name)
            assertEquals("${got.id} description", want.getString("description"), got.desc)

            val groups = want.getJSONArray("groups")
            assertEquals("${got.id} group count", groups.length(), got.groups.size)
            for (g in 0 until groups.length()) {
                val wantGroup = groups.getJSONObject(g)
                val gotGroup = got.groups[g]
                assertEquals("${got.id} group $g label", wantGroup.getString("label"), gotGroup.label)
                assertEquals("${got.id} ${gotGroup.label} day", wantGroup.getInt("dayOffset"), gotGroup.days)

                val vaccines = wantGroup.getJSONArray("vaccines")
                assertEquals("${got.id} ${gotGroup.label} count", vaccines.length(), gotGroup.vaccines.size)
                for (v in 0 until vaccines.length()) {
                    val wantVaccine = vaccines.getJSONObject(v)
                    val gotVaccine = gotGroup.vaccines[v]
                    assertEquals(
                        "${got.id} ${gotGroup.label} vaccine $v",
                        wantVaccine.getString("name"),
                        gotVaccine.name,
                    )
                    assertEquals(
                        "${got.id} ${gotGroup.label} ${gotVaccine.name} description",
                        wantVaccine.getString("description"),
                        gotVaccine.desc,
                    )
                }
            }
        }
    }

    /**
     * L, M and S are private, so each row is pinned through the two public functions that use
     * them: the median is M outright, and a percentile weight recomputed here from the
     * published triple exercises L and S together. A single mistyped digit in any of the three
     * moves one of the two.
     */
    @Test
    fun `the compiled growth tables are what common data says`() {
        val table = read("who-weight-for-age.json")
        assertEquals("maxMonths", table.getInt("maxMonths"), GrowthStandards.MAX_MONTHS)

        val bands = table.getJSONArray("bands")
        assertEquals("band count", bands.length(), GrowthStandards.BANDS.size)
        for (i in 0 until bands.length()) {
            assertEquals("band $i", bands.getInt(i), GrowthStandards.BANDS[i])
        }

        listOf("boys" to Sex.BOY, "girls" to Sex.GIRL).forEach { (key, sex) ->
            val rows = table.getJSONArray(key)
            assertEquals("$key row count", GrowthStandards.MAX_MONTHS + 1, rows.length())
            for (month in 0 until rows.length()) {
                val row = rows.getJSONObject(month)
                val l = row.getDouble("l")
                val m = row.getDouble("m")
                val s = row.getDouble("s")
                val age = month.toDouble()

                assertEquals(
                    "$key $month mo median",
                    m,
                    GrowthStandards.medianWeightKg(age, sex)!!,
                    1e-9,
                )
                // The same LMS formula the app uses, applied to the published triple.
                val z = GrowthStandards.zForPercentile(3)
                val expected =
                    if (abs(l) < 1e-7) m * exp(s * z) else m * (1 + l * s * z).pow(1 / l)
                assertEquals(
                    "$key $month mo 3rd percentile",
                    expected,
                    GrowthStandards.weightAtPercentile(age, sex, 3)!!,
                    1e-9,
                )
            }
        }
    }
}
