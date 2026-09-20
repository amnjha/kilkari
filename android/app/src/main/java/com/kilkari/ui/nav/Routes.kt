package com.kilkari.ui.nav

import com.kilkari.ui.components.NavTab
import com.kilkari.ui.theme.Accent
import com.kilkari.ui.theme.KAccents

/** Every destination in the app. The five [tabs] own the bottom bar; the rest are pushed. */
object Routes {
    const val ONBOARDING = "onboarding"

    const val TODAY = "today"
    const val LOG = "log"
    const val HEALTH = "health"
    const val MONEY = "money"
    const val MORE = "more"

    const val VACCINES = "vaccines"
    const val VACCINE_DETAIL = "vaccineDetail"
    const val GROWTH = "growth"
    const val TEETH = "teeth"
    const val MEDS = "meds"
    const val APPOINTMENTS = "appointments"
    const val DOCTORS = "doctors"

    const val CATCH_UP = "catchUp"
    const val RECONCILE = "reconcile"
    const val LOG_DAY = "logDay"
    const val INSIGHTS = "insights"

    const val TIMELINE = "timeline"
    const val DOCUMENTS = "documents"
    const val DOCUMENT_DETAIL = "documentDetail"
    const val PAPERWORK = "paperwork"
    const val PHOTOS = "photos"
    const val EVENTS = "events"
    const val REMINDERS = "reminders"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"

    val tabs = listOf(
        NavTab(TODAY, "Today", "sunny", KAccents.Brand),
        NavTab(LOG, "Log", "edit_note", KAccents.Day),
        NavTab(HEALTH, "Health", "favorite", KAccents.Health),
        NavTab(MONEY, "Money", "payments", KAccents.Money),
        NavTab(MORE, "More", "grid_view", KAccents.Quiet),
    )

    /** Which tab stays lit while a pushed screen is on top. */
    private val tabOf = mapOf(
        VACCINES to HEALTH, VACCINE_DETAIL to HEALTH, GROWTH to HEALTH,
        TEETH to HEALTH, MEDS to HEALTH, APPOINTMENTS to HEALTH, DOCTORS to HEALTH,
        LOG_DAY to LOG, INSIGHTS to LOG, CATCH_UP to MORE, RECONCILE to MONEY,
        TIMELINE to MORE, DOCUMENTS to MORE, DOCUMENT_DETAIL to MORE, PAPERWORK to MORE, PHOTOS to MORE,
        EVENTS to MORE, REMINDERS to MORE, BACKUP to MORE, SETTINGS to MORE,
    )

    /**
     * The colour each destination wears.
     *
     * Grouped by what the screen is about rather than by which tab it hangs off: the vaccine
     * list and an appointment are both clinical, the timeline and the albums are both
     * keepsakes, and the fund and its reconciliation are both money. Sibling screens sharing
     * a hue is the point — it is how a pushed screen still feels like where it came from.
     */
    fun accentFor(route: String?): Accent = when (route) {
        LOG, LOG_DAY -> KAccents.Day
        HEALTH, VACCINES, VACCINE_DETAIL -> KAccents.Health
        GROWTH, TEETH -> KAccents.Growth
        MEDS, CATCH_UP -> KAccents.Care
        APPOINTMENTS -> KAccents.Brand
        MONEY, RECONCILE -> KAccents.Money
        TIMELINE, PHOTOS, EVENTS -> KAccents.Memories
        DOCUMENTS, DOCUMENT_DETAIL, PAPERWORK, DOCTORS, BACKUP -> KAccents.Records
        MORE, SETTINGS, REMINDERS, INSIGHTS, ONBOARDING -> KAccents.Quiet
        else -> KAccents.Brand
    }

    fun activeTab(route: String?): String = route?.let { tabOf[it] ?: it } ?: TODAY

    /** Onboarding is full-bleed; every other destination keeps the bottom bar. */
    fun showsNav(route: String?): Boolean = route != null && route != ONBOARDING
}
