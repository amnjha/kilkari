package com.kilkari.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.repo.BackupManager
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KRow
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val BACKUP_MIME = "application/zip"

/** Backup, CSV export, a printable vaccination record, and restore — all through the file picker. */
@Composable
fun BackupScreen(vm: KilkariViewModel, go: NavActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()
    val groups by vm.vaccineGroups.collectAsStateWithLifecycle()

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.write(uri) { BackupManager.writeBackup(context, it) }
            vm.toast(bytes?.let { "Backup saved · ${formatSize(it)}" } ?: "Could not write backup")
        }
    }

    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = context.write(uri) { BackupManager.writeCsv(context, it, settings.currency) }
            vm.toast(if (ok != null) "CSV exported" else "Could not write CSV")
        }
    }

    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val b = baby
        if (uri == null || b == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = context.write(uri) { stream ->
                BackupManager.writeVaccinationPdf(
                    context, stream, b.name, b.dob,
                    VaccineSchedules.byId(settings.scheduleId).name, groups,
                )
            }
            vm.toast(if (ok != null) "Vaccination record saved" else "Could not write PDF")
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val restored = runCatching { BackupManager.restoreBackup(context, uri) }.getOrDefault(false)
            vm.toast(
                if (restored) "Restored · close and reopen Kilkari"
                else "That file is not a Kilkari backup"
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailBar("Backup & export", go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KCard(background = KC.GreenBg, border = KC.GreenRing) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KIcons["cloud_done"], null, tint = KC.Green, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Everything is on this phone",
                            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Ink,
                        )
                        Text(
                            "No account needed. A backup is a single file you choose where to put.",
                            fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp, color = KC.MutedStrong,
                        )
                    }
                }
            }

            KCard {
                KRow(
                    title = "Back up now",
                    subtitle = "Database and scans in one .kilkari file",
                    icon = "backup",
                    onClick = { saveBackup.launch(backupName()) },
                )
                KRow(
                    title = "Export data as CSV",
                    subtitle = "Logs, growth, vaccines, expenses, timeline, events",
                    icon = "table_view",
                    onClick = { saveCsv.launch("kilkari-${LocalDate.now()}.csv") },
                )
                KRow(
                    title = "Vaccination record PDF",
                    subtitle = "Share with school or clinic",
                    icon = "picture_as_pdf",
                    onClick = { savePdf.launch("vaccination-record-${LocalDate.now()}.pdf") },
                )
                KRow(
                    title = "Restore from backup",
                    subtitle = "Replaces everything currently on this phone",
                    icon = "restore",
                    divider = false,
                    onClick = { openBackup.launch(arrayOf(BACKUP_MIME, "application/octet-stream", "*/*")) },
                )
            }

            KCard {
                KRow(
                    title = "Auto-backup weekly",
                    subtitle = "Reminds you every Sunday to save a fresh copy",
                    divider = false,
                    onClick = { vm.setAutoBackup(!settings.autoBackup) },
                ) {
                    KSwitch(settings.autoBackup)
                }
            }

            Text(
                "Backups are not encrypted — put them somewhere you trust. " +
                    "Restoring needs an app restart to take effect.",
                fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
            )
        }
    }
}

/** Runs [block] against the picked destination, returning null when the write fails. */
private suspend fun <T> Context.write(uri: Uri, block: suspend (java.io.OutputStream) -> T): T? =
    runCatching {
        contentResolver.openOutputStream(uri, "wt")?.use { block(it) }
    }.getOrNull()

private fun backupName() = "kilkari-${LocalDate.now()}.kilkari"

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
