package com.kilkari.ui.nav

import com.kilkari.ui.components.NavTab

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
        NavTab(TODAY, "Today", "sunny"),
        NavTab(LOG, "Log", "edit_note"),
        NavTab(HEALTH, "Health", "favorite"),
        NavTab(MONEY, "Money", "payments"),
        NavTab(MORE, "More", "grid_view"),
    )

    /** Which tab stays lit while a pushed screen is on top. */
    private val tabOf = mapOf(
        VACCINES to HEALTH, VACCINE_DETAIL to HEALTH, GROWTH to HEALTH,
        TEETH to HEALTH, MEDS to HEALTH, APPOINTMENTS to HEALTH, DOCTORS to HEALTH,
        LOG_DAY to LOG, INSIGHTS to LOG, CATCH_UP to MORE, RECONCILE to MONEY,
        TIMELINE to MORE, DOCUMENTS to MORE, DOCUMENT_DETAIL to MORE, PAPERWORK to MORE, PHOTOS to MORE,
        EVENTS to MORE, REMINDERS to MORE, BACKUP to MORE, SETTINGS to MORE,
    )

    fun activeTab(route: String?): String = route?.let { tabOf[it] ?: it } ?: TODAY

    /** Onboarding is full-bleed; every other destination keeps the bottom bar. */
    fun showsNav(route: String?): Boolean = route != null && route != ONBOARDING
}
