package com.kilkari.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KRow
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * Per-category notification switches. Turning the first one on asks for the runtime
 * notification permission, since without it the worker posts nothing.
 */
@Composable
fun RemindersScreen(vm: KilkariViewModel, go: NavActions) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.toast("Reminders need notification permission")
    }

    fun ensurePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(Modifier.fillMaxSize()) {
        DetailBar("Reminders", go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KCard {
                reminders.forEachIndexed { i, reminder ->
                    KRow(
                        title = reminder.title,
                        subtitle = reminder.subtitle,
                        divider = i != reminders.lastIndex,
                        onClick = {
                            if (!reminder.enabled) ensurePermission()
                            vm.setReminderEnabled(reminder, !reminder.enabled)
                        },
                    ) {
                        KSwitch(reminder.enabled)
                    }
                }
            }
            Text(
                "Kilkari checks once a day and only notifies about what is actually due. " +
                    "Nothing is sent anywhere — the checks run on this phone.",
                fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
            )
        }
    }
}
