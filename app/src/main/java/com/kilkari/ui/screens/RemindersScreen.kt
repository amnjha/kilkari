package com.kilkari.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.ReminderEntity
import com.kilkari.domain.RepeatRule
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KRow
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.ReminderSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * Notification switches for the built-in prompts, plus any reminders the parent adds
 * themselves. Everything here also feeds the Today screen's due list.
 */
@Composable
fun RemindersScreen(vm: KilkariViewModel, go: NavActions) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /** Non-null while the sheet is open; the inner value is null for a new reminder. */
    var editing by remember { mutableStateOf<Pair<ReminderEntity?, Boolean>?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.toast("Reminders need notification permission")
    }

    fun ensurePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val builtIn = reminders.filter { it.builtIn }
    val custom = reminders.filterNot { it.builtIn }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Reminders", go::back) {
                IconButton44("add", KC.Indigo, { ensurePermission(); editing = null to true }, iconSize = 26)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionLabel("Built in")
                KCard {
                    builtIn.forEachIndexed { i, reminder ->
                        KRow(
                            title = reminder.title,
                            subtitle = reminder.subtitle,
                            divider = i != builtIn.lastIndex,
                            onClick = {
                                if (!reminder.enabled) ensurePermission()
                                vm.setReminderEnabled(reminder, !reminder.enabled)
                            },
                        ) {
                            KSwitch(reminder.enabled)
                        }
                    }
                }

                SectionLabel("Yours")
                KCard {
                    if (custom.isEmpty()) {
                        Text(
                            "None yet. Tap + to add a reminder with its own time and cadence.",
                            modifier = Modifier.padding(14.dp),
                            fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
                        )
                    }
                    custom.forEachIndexed { i, reminder ->
                        KRow(
                            title = reminder.title,
                            subtitle = describe(reminder),
                            divider = i != custom.lastIndex,
                            onClick = { editing = reminder to true },
                        ) {
                            KSwitch(reminder.enabled) {
                                if (!reminder.enabled) ensurePermission()
                                vm.setReminderEnabled(reminder, !reminder.enabled)
                            }
                        }
                    }
                }

                Text(
                    "Kilkari checks once a day and only notifies about what is actually due. " +
                        "Anything that repeats less often than daily stays on Today until you " +
                        "tick it off or dismiss it.",
                    fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
                )
            }
        }

        val open = editing
        KSheet(open != null, onDismiss = { editing = null }) {
            if (open != null) {
                ReminderSheet(
                    existing = open.first,
                    onSave = { key, title, note, minute, repeat, weekday, dayOfMonth, date ->
                        vm.saveCustomReminder(key, title, note, minute, repeat, weekday, dayOfMonth, date)
                        editing = null
                    },
                    onDelete = open.first?.let { existing ->
                        {
                            vm.deleteReminder(existing.key)
                            editing = null
                        }
                    },
                )
            }
        }
    }
}

/** Plain-English cadence for a custom reminder's subtitle. */
private fun describe(r: ReminderEntity): String {
    val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val time = r.minuteOfDay?.let { "%d:%02d %s".format(
        ((it / 60) % 12).let { h -> if (h == 0) 12 else h }, it % 60, if (it < 720) "am" else "pm",
    ) }
    val cadence = when (RepeatRule.of(r.repeatRule)) {
        RepeatRule.DAILY -> "Every day"
        RepeatRule.WEEKLY -> "Every ${weekdays[((r.weekday ?: 7) - 1).coerceIn(0, 6)]}"
        RepeatRule.MONTHLY -> "Day ${r.dayOfMonth ?: 1} each month"
        RepeatRule.NONE -> r.startDate?.let { com.kilkari.domain.Fmt.dateFull(it) } ?: "Once"
    }
    return listOfNotNull(cadence, time, r.subtitle.ifBlank { null }).joinToString(" · ")
}
