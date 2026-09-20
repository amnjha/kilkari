package com.kilkari.domain

/**
 * One position in an arch, left to right as the chart draws it.
 *
 * [name] is kept short because it has to sit in a half-width card without wrapping into a
 * paragraph; the jaw is added alongside it wherever it is shown.
 */
data class ToothSpec(val name: String, val fromMonth: Int, val toMonth: Int)

/**
 * The twenty primary teeth and the age range each usually comes through, following the
 * ADA / AAPD primary tooth eruption chart.
 *
 * Positions run second molar, first molar, canine, lateral incisor, central incisor and back
 * out again — the order they sit in the mouth, not the order they arrive. The centrals in the
 * middle come first and the second molars at the ends come last, which is why the next tooth
 * due has to be looked up rather than read off the end of the list.
 */
object ToothChart {

    val UPPER: List<ToothSpec> = listOf(
        ToothSpec("2nd molar", 25, 33),
        ToothSpec("1st molar", 13, 19),
        ToothSpec("canine", 16, 22),
        ToothSpec("lateral", 9, 13),
        ToothSpec("central", 8, 12),
        ToothSpec("central", 8, 12),
        ToothSpec("lateral", 9, 13),
        ToothSpec("canine", 16, 22),
        ToothSpec("1st molar", 13, 19),
        ToothSpec("2nd molar", 25, 33),
    )

    val LOWER: List<ToothSpec> = listOf(
        ToothSpec("2nd molar", 23, 31),
        ToothSpec("1st molar", 14, 18),
        ToothSpec("canine", 17, 23),
        ToothSpec("lateral", 10, 16),
        ToothSpec("central", 6, 10),
        ToothSpec("central", 6, 10),
        ToothSpec("lateral", 10, 16),
        ToothSpec("canine", 17, 23),
        ToothSpec("1st molar", 14, 18),
        ToothSpec("2nd molar", 23, 31),
    )

    /** Total in a full set of primary teeth. */
    const val TOTAL = 20

    /** Every position with the code that identifies it, so one list can be searched. */
    private val ALL: List<Pair<String, ToothSpec>> =
        UPPER.mapIndexed { i, spec -> "u$i" to spec } + LOWER.mapIndexed { i, spec -> "l$i" to spec }

    fun specFor(code: String): ToothSpec = ALL.first { it.first == code }.second

    fun jawOf(code: String): String = if (code.startsWith("u")) "Upper" else "Lower"

    /** "Lower central", the way a tooth is named on screen. */
    fun labelFor(code: String): String = "${jawOf(code)} ${specFor(code).name}"

    /**
     * The tooth due next: of those not yet recorded, the one whose window opens earliest.
     *
     * Null once all twenty are through. Deliberately not compared against the child's age — a
     * tooth that is late is still the next one to watch for, so the chart keeps pointing at it
     * rather than skipping ahead to one that is not due yet.
     */
    fun nextExpected(erupted: Set<String>): Pair<String, ToothSpec>? =
        ALL.filterNot { it.first in erupted }
            .minWithOrNull(compareBy({ it.second.fromMonth }, { it.second.toMonth }))
}
