package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.MilestoneSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

/** Milestones, births, vaccinations and photo moments on one spine. */
@Composable
fun TimelineScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar(baby?.let { "${it.name}'s timeline" } ?: "Timeline", go::back) {
                IconButton44("add", KC.Indigo, { sheetOpen = true }, iconSize = 26)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
            ) {
                if (timeline.isEmpty()) {
                    Text(
                        "Nothing here yet. Tap + to add the first moment.",
                        fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                    )
                }
                timeline.forEachIndexed { i, entry ->
                    val skin = timelineSkin(entry.icon)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Intrinsic height lets the spine below stretch to the entry's height.
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            Modifier
                                .width(36.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(skin.second),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    KIcons[entry.icon], null,
                                    tint = skin.first, modifier = Modifier.size(18.dp),
                                )
                            }
                            if (i != timeline.lastIndex) {
                                Box(
                                    Modifier
                                        .padding(vertical = 4.dp)
                                        .width(2.dp)
                                        .weight(1f)
                                        .background(KC.BorderStrong),
                                )
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(bottom = 16.dp),
                        ) {
                            Text(
                                listOfNotNull(
                                    Fmt.date(entry.date),
                                    baby?.dob?.let { Fmt.ageShort(it, entry.date) },
                                ).joinToString(" · "),
                                fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp, color = KC.Muted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                entry.title,
                                fontFamily = Sans, fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, color = KC.Ink,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            if (entry.subtitle.isNotBlank()) {
                                Text(
                                    entry.subtitle,
                                    fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp,
                                    color = KC.MutedStrong, modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            entry.albumUrl?.let { url ->
                                Row(
                                    Modifier
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(KC.VioletBg)
                                        .clickable { openLink(context, url) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        KIcons["open_in_new"], null,
                                        tint = KC.VioletDeep, modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "Open photo album",
                                        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp, color = KC.VioletDeep,
                                    )
                                }
                            }
                            Text(
                                "Remove",
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clickable { vm.deleteTimelineEntry(entry) },
                                fontFamily = Sans, fontSize = 12.sp, color = KC.Faint,
                            )
                        }
                    }
                }
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            MilestoneSheet { title, note, album ->
                vm.addMilestone(title, note, LocalDate.now(), album)
                sheetOpen = false
            }
        }
    }
}

/** Icon colour pairing per timeline entry kind. */
internal fun timelineSkin(icon: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (icon) {
        "favorite" -> KC.Rose to KC.RoseBg
        "vaccines" -> KC.VioletDeep to KC.VioletBg
        "celebration" -> KC.FuchsiaDeep to KC.FuchsiaBg
        "child_care" -> KC.Cyan to KC.CyanBg
        "home" -> KC.Orange to KC.OrangeBg
        "cake" -> KC.Rose to KC.RoseBg
        else -> KC.FuchsiaDeep to KC.FuchsiaBg
    }

internal fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
