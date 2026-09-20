package com.kilkari.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.LocalAccent
import com.kilkari.ui.theme.Sans

/**
 * Every place the app has room for a drawing.
 *
 * Each slot names a drawable that may or may not exist yet. Until the artwork is dropped into
 * `res/drawable-nodpi/` under exactly this name, the slot draws a coloured blob carrying the
 * screen's icon instead — so the layout is already built for the picture, nothing crashes on
 * a missing file, and adding the art later is a copy, not a code change.
 *
 * [fallbackIcon] is the glyph the blob shows in the meantime, and is also what the drawing
 * should be recognisably about.
 */
enum class Spot(val file: String, val fallbackIcon: String) {

    // Onboarding — one per step, read at roughly 200dp tall.
    ONBOARD_BABY("art_onboard_baby", "child_care"),
    ONBOARD_MEASUREMENTS("art_onboard_measurements", "monitor_weight"),
    ONBOARD_SCHEDULE("art_onboard_schedule", "vaccines"),
    ONBOARD_MONEY("art_onboard_money", "savings"),
    ONBOARD_DONE("art_onboard_done", "check_circle"),

    // Empty states — the first thing a parent sees on a screen they have not used yet.
    EMPTY_TIMELINE("art_empty_timeline", "timeline"),
    EMPTY_PHOTOS("art_empty_photos", "photo_library"),
    EMPTY_DOCUMENTS("art_empty_documents", "folder_open"),
    EMPTY_APPOINTMENTS("art_empty_appointments", "stethoscope"),
    EMPTY_DOCTORS("art_empty_doctors", "medical_services"),
    EMPTY_EVENTS("art_empty_events", "celebration"),
    EMPTY_MEDICINES("art_empty_medicines", "pill"),
    EMPTY_REMINDERS("art_empty_reminders", "notifications_active"),
    EMPTY_GROWTH("art_empty_growth", "monitor_weight"),
    EMPTY_MONEY("art_empty_money", "savings"),
    EMPTY_LOG("art_empty_log", "edit"),

    // A moment worth marking: a vaccine course finished, a first tooth, a milestone caught up.
    CELEBRATE("art_celebrate", "celebration"),
}

/** The artwork for [spot], or null while the file has not been added yet. */
@Composable
fun spotPainter(spot: Spot): Painter? {
    val context = LocalContext.current
    // Resolved by name rather than by R.drawable so a slot can ship before its picture does.
    val id = remember(spot) {
        @Suppress("DiscouragedApi")
        context.resources.getIdentifier(spot.file, "drawable", context.packageName)
    }
    return if (id == 0) null else painterResource(id)
}

/**
 * A drawing at [size], falling back to a soft blob of the screen's colour with the slot's icon
 * inside it. The blob is deliberately pleasant rather than a grey placeholder box: a screen
 * with no artwork yet still looks finished.
 */
@Composable
fun Illustration(spot: Spot, size: Dp = 160.dp, modifier: Modifier = Modifier) {
    val painter = spotPainter(spot)
    val accent = LocalAccent.current
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 42))
                .background(Brush.linearGradient(listOf(accent.wash, accent.bg))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                KIcons[spot.fallbackIcon], null,
                tint = accent.deep,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

/**
 * What a screen shows before it has anything to show: a drawing, a line that says what the
 * screen is for, and a line that says how to start. It replaced a grey sentence — the moment
 * a parent is most likely to give up on a feature is the moment it looks broken.
 */
@Composable
fun EmptyState(
    spot: Spot,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Illustration(spot, size)
        Text(
            title,
            fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            color = KC.Ink, textAlign = TextAlign.Center,
        )
        Text(
            body,
            fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp,
            color = KC.Muted, textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
