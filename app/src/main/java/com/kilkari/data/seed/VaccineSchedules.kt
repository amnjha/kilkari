package com.kilkari.data.seed

/**
 * Static vaccination schedules. Due dates are derived per-baby by offsetting [VaccineGroupDef.days]
 * from the date of birth, so the same definition serves any baby.
 */
data class VaccineDef(val name: String, val desc: String)

data class VaccineGroupDef(val label: String, val days: Int, val vaccines: List<VaccineDef>)

data class ScheduleDef(
    val id: String,
    val name: String,
    val desc: String,
    val groups: List<VaccineGroupDef>,
) {
    /** "IAP" out of "IAP · India (private)" — used wherever the design shows the short chip label. */
    val shortName: String get() = name.substringBefore(" ·").trim()
}

private fun g(label: String, days: Int, vararg v: Pair<String, String>) =
    VaccineGroupDef(label, days, v.map { VaccineDef(it.first, it.second) })

object VaccineSchedules {

    val IAP = ScheduleDef(
        id = "iap",
        name = "IAP · India (private)",
        desc = "Indian Academy of Pediatrics 2024",
        groups = listOf(
            g("Birth", 0,
                "BCG" to "Tuberculosis",
                "OPV-0" to "Polio, oral",
                "Hep B-1" to "Hepatitis B"),
            g("6 weeks", 42,
                "DTwP/DTaP-1" to "Diphtheria, tetanus, pertussis",
                "IPV-1" to "Polio, injectable",
                "Hep B-2" to "Hepatitis B",
                "Hib-1" to "H. influenzae b",
                "Rotavirus-1" to "Oral",
                "PCV-1" to "Pneumococcal"),
            g("10 weeks", 70,
                "DTwP/DTaP-2" to "", "IPV-2" to "", "Hib-2" to "",
                "Rotavirus-2" to "", "PCV-2" to ""),
            g("14 weeks", 98,
                "DTwP/DTaP-3" to "", "IPV-3" to "", "Hep B-3" to "",
                "Hib-3" to "", "Rotavirus-3" to "", "PCV-3" to ""),
            g("6 months", 182,
                "Influenza-1" to "Yearly after 2 doses",
                "Typhoid conjugate" to ""),
            g("7 months", 212, "Influenza-2" to ""),
            g("9 months", 274, "MMR-1" to "Measles, mumps, rubella"),
            g("12 months", 365, "Hep A-1" to "", "PCV booster" to ""),
            g("15 months", 456, "MMR-2" to "", "Varicella-1" to "Chickenpox"),
            g("18 months", 547,
                "DTwP booster-1" to "", "IPV booster" to "", "Hib booster" to "",
                "Hep A-2" to "", "Varicella-2" to ""),
        ),
    )

    val UIP = ScheduleDef(
        id = "uip",
        name = "UIP · India (government)",
        desc = "National Immunisation Schedule",
        groups = listOf(
            g("Birth", 0, "BCG" to "", "OPV-0" to "", "Hep B birth dose" to ""),
            g("6 weeks", 42,
                "OPV-1" to "", "Pentavalent-1" to "DPT + Hep B + Hib",
                "Rotavirus-1" to "", "fIPV-1" to "", "PCV-1" to ""),
            g("10 weeks", 70, "OPV-2" to "", "Pentavalent-2" to "", "Rotavirus-2" to ""),
            g("14 weeks", 98,
                "OPV-3" to "", "Pentavalent-3" to "", "Rotavirus-3" to "",
                "fIPV-2" to "", "PCV-2" to ""),
            g("9 months", 274,
                "MR-1" to "Measles, rubella", "JE-1" to "Japanese encephalitis",
                "PCV booster" to "", "Vitamin A-1" to ""),
            g("16–24 months", 487,
                "MR-2" to "", "JE-2" to "", "DPT booster-1" to "", "OPV booster" to ""),
        ),
    )

    val WHO = ScheduleDef(
        id = "who",
        name = "WHO · global",
        desc = "WHO recommended routine schedule",
        groups = listOf(
            g("Birth", 0, "BCG" to "", "Hep B" to "", "OPV-0" to ""),
            g("6 weeks", 42,
                "DTP-1" to "", "Hep B-2" to "", "Hib-1" to "",
                "IPV-1" to "", "PCV-1" to "", "Rotavirus-1" to ""),
            g("10 weeks", 70,
                "DTP-2" to "", "Hib-2" to "", "OPV-2" to "",
                "PCV-2" to "", "Rotavirus-2" to ""),
            g("14 weeks", 98,
                "DTP-3" to "", "Hep B-3" to "", "Hib-3" to "",
                "IPV-2" to "", "PCV-3" to ""),
            g("9 months", 274, "Measles-1" to "", "Rubella" to ""),
            g("15–18 months", 487, "Measles-2" to "", "DTP booster" to ""),
        ),
    )

    val CDC = ScheduleDef(
        id = "cdc",
        name = "CDC · United States",
        desc = "ACIP child schedule",
        groups = listOf(
            g("Birth", 0, "Hep B-1" to ""),
            g("2 months", 61,
                "DTaP-1" to "", "IPV-1" to "", "Hib-1" to "",
                "PCV-1" to "", "Rotavirus-1" to "", "Hep B-2" to ""),
            g("4 months", 122,
                "DTaP-2" to "", "IPV-2" to "", "Hib-2" to "",
                "PCV-2" to "", "Rotavirus-2" to ""),
            g("6 months", 182,
                "DTaP-3" to "", "IPV-3" to "", "PCV-3" to "",
                "Hep B-3" to "", "Influenza" to "Yearly"),
            g("12 months", 365,
                "MMR-1" to "", "Varicella-1" to "", "Hep A-1" to "",
                "PCV-4" to "", "Hib booster" to ""),
            g("15 months", 456, "DTaP-4" to ""),
        ),
    )

    val all: List<ScheduleDef> = listOf(IAP, UIP, WHO, CDC)

    fun byId(id: String): ScheduleDef = all.firstOrNull { it.id == id } ?: IAP
}
