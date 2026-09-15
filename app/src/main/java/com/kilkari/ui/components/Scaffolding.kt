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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.ui.theme.BarTitle
import com.kilkari.ui.theme.KC
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

/** The five-tab bar: Today · Log · Health · Money · More. */
@Composable
fun KBottomNav(tabs: List<NavTab>, active: String, onSelect: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(KC.Surface),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Border))
        Row(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        ) {
            tabs.forEach { tab ->
                val on = tab.route == active
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (on) KC.IndigoBg else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            KIcons[tab.icon], contentDescription = tab.label,
                            tint = if (on) KC.Ink else KC.Muted,
                            modifier = Modifier.size(22.dp),
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
            Icon(KIcons["check_circle"], null, tint = KC.IndigoPaler, modifier = Modifier.size(20.dp))
            Text(
                message.orEmpty(),
                color = Color.White, fontFamily = Sans,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
        }
    }
}

/** Square FAB matching the design's 56dp / 18dp-corner button. */
@Composable
fun BoxScope.KFab(icon: String, label: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(KC.Indigo)
            .clickable(onClick = onClick)
            .padding(horizontal = if (label == null) 16.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KIcons[icon], null, tint = Color.White, modifier = Modifier.size(28.dp))
        if (label != null) {
            Text(label, color = Color.White, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
