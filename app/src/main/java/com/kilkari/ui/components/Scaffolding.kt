package com.kilkari.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.ui.theme.BarTitle
import androidx.compose.ui.graphics.Brush
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.springPress
import com.kilkari.ui.theme.clay
import com.kilkari.ui.theme.Sans

/** Back arrow + display title + optional action, the header on every detail screen. */
@Composable
fun DetailBar(
    title: String,
    onBack: () -> Unit,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton44("arrow_back", KC.Ink, onBack)
        Text(title, style = BarTitle, color = KC.Ink, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun IconButton44(icon: String, tint: Color, onClick: () -> Unit, iconSize: Int = 24) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(KIcons[icon], contentDescription = null, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

data class NavTab(val route: String, val label: String, val icon: String)

/**
 * The five-tab bar: Today · Log · Health · Money · More.
 *
 * A floating rounded bar rather than a strip welded to the bottom edge: it reads as part of
 * the same family of soft, raised things the rest of the app is built from, and the active tab
 * carries a coral pill so where you are is obvious at a glance rather than a shade of grey.
 */
@Composable
fun KBottomNav(tabs: List<NavTab>, active: String, onSelect: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clay(corner = 28, elevation = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(KC.Surface)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val on = tab.route == active
                val press = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .springPress(press)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = press, indication = null) { onSelect(tab.route) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) KC.Coral else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            KIcons[tab.icon], contentDescription = tab.label,
                            tint = if (on) Color.White else KC.Muted,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Text(
                        tab.label,
                        fontFamily = Sans, fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) KC.Ink else KC.Muted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * Bottom sheet drawn to the design's spec: scrim, 28dp top corners, grab handle, and a
 * scrollable body. Built on [ModalBottomSheet] so it renders in its own window and covers
 * the bottom navigation, the way the design's full-bleed scrim does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KC.Surface,
        scrimColor = Color(0x731E1B4B),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KC.Track),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Dark pill toast floating above the nav bar. */
@Composable
fun BoxScope.KToast(message: String?, bottomInset: Dp = 16.dp) {
    // Hold the last message: on dismissal `message` is already null while the exit animation
    // still composes the pill, which would otherwise render empty.
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(message) { if (message != null) shown = message }

    AnimatedVisibility(
        visible = message != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomInset)
                .clip(RoundedCornerShape(14.dp))
                .background(KC.Ink)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KIcons["check_circle"], null, tint = KC.CoralPaler, modifier = Modifier.size(20.dp))
            Text(
                shown,
                color = Color.White, fontFamily = Sans,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
        }
    }
}

/** Square FAB matching the design's 56dp / 18dp-corner button. */
@Composable
fun BoxScope.KFab(icon: String, label: String? = null, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .align(Alignment.BottomEnd)
            // Clear of the floating bar, which now stands off the bottom edge itself.
            .padding(end = 16.dp, bottom = 22.dp)
            .height(58.dp)
            .springPress(press)
            .clay(corner = 22, elevation = 16.dp, tint = KC.Coral)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(KC.Coral, KC.CoralDeep)))
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = if (label == null) 17.dp else 21.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KIcons[icon], null, tint = Color.White, modifier = Modifier.size(28.dp))
        if (label != null) {
            Text(label, color = Color.White, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
