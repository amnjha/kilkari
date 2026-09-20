package com.kilkari.data.seed

/**
 * Common infant milestones with the age they typically appear. Used only to *offer* entries
 * during catch-up — a parent starting the app late can tick what has already happened rather
 * than reconstructing it from memory later. Ages are rough guides, not expectations.
 */
data class MilestoneDef(
    val key: String,
    val label: String,
    val typicalMonths: Double,
    val icon: String,
)

object Milestones {
    val all = listOf(
        MilestoneDef("smile", "First real smile", 1.5, "child_care"),
        MilestoneDef("head", "Held head up", 3.0, "child_care"),
        MilestoneDef("roll", "Rolled over", 4.0, "auto_awesome"),
        MilestoneDef("laugh", "First laugh", 4.0, "celebration"),
        MilestoneDef("sit", "Sat without support", 6.0, "auto_awesome"),
        MilestoneDef("solids", "Started solids", 6.0, "restaurant"),
        MilestoneDef("tooth", "First tooth", 6.5, "dentistry"),
        MilestoneDef("crawl", "Started crawling", 9.0, "auto_awesome"),
        MilestoneDef("stand", "Stood holding on", 9.5, "auto_awesome"),
        MilestoneDef("word", "First word", 12.0, "celebration"),
        MilestoneDef("steps", "First steps", 12.5, "auto_awesome"),
        MilestoneDef("walk", "Walked on their own", 14.0, "auto_awesome"),
    )

    /** Milestones a baby of [ageMonths] would typically already have reached. */
    fun passedBy(ageMonths: Double): List<MilestoneDef> =
        all.filter { it.typicalMonths <= ageMonths }
}
