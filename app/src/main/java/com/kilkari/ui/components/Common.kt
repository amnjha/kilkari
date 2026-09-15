package com.kilkari.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * Lets a child escape its parent's horizontal padding and run edge to edge — Compose rejects
 * negative padding, so the width is widened and the child shifted during layout instead.
 */
fun Modifier.bleedHorizontal(amount: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            maxWidth = if (constraints.maxWidth == Constraints.Infinity) constraints.maxWidth
            else constraints.maxWidth + extra,
        )
    )
    layout(placeable.width - extra, placeable.height) {
        placeable.place(-amount.roundToPx(), 0)
    }
}

/** The white, hairline-bordered, 18dp-rounded card that carries almost every list in the app. */
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    corner: Int = 18,
    background: Color = KC.Surface,
    border: Color? = KC.Border,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = shape,
        color = background,
        border = border?.let { BorderStroke(1.dp, it) },
        content = { Column(content = content) },
    )
}

/** Card whose ground is a brand gradient — the Next-up and Vaccinations hero cards. */
@Composable
fun GradientCard(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    corner: Int = 20,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Rounded square icon chip used as the leading element of most rows and tiles. */
@Composable
fun IconBadge(
    icon: String,
    tint: Color,
    background: Color,
    size: Int = 40,
    corner: Int = 12,
    iconSize: Int = 22,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(KIcons[icon], contentDescription = null, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

/** Circular monogram avatar on the indigo → fuchsia ramp. */
@Composable
fun Monogram(letter: String, size: Int = 44, fontSize: Int = 18) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Brush.linearGradient(listOf(KC.CoralLight, KC.GoldLight))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = Color.White,
            fontFamily = Sans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize.sp,
        )
    }
}

/** Pill button used for filters (Money, Settings currency, milestone chips). */
@Composable
fun KChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) KC.Coral else KC.Surface,
        border = BorderStroke(1.dp, if (selected) KC.Coral else KC.BorderStrong),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else KC.CoralDeep,
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
        }
    }
}

/** Segmented control on a lilac track — Feed type, expense category. */
@Composable
fun KSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(KC.ClayBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) KC.Coral else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Color.White else KC.CoralDeep,
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }
        }
    }
}

/** Full-width 50dp pill — the primary action at the bottom of every sheet. */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) KC.Coral else KC.CoralPale)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Outlined counterpart to [PrimaryButton]. */
@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = KC.Surface,
        border = BorderStroke(1.dp, KC.BorderStrong),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(KIcons[icon], null, tint = KC.CoralDeep, modifier = Modifier.size(20.dp))
            Text(label, color = KC.CoralDeep, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** iOS-style switch drawn to the design's 44×26 spec. */
@Composable
fun KSwitch(checked: Boolean, onToggle: (() -> Unit)? = null) {
    Box(
        Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) KC.Coral else KC.Track)
            .let { if (onToggle != null) it.clickable(onClick = onToggle) else it },
    ) {
        Box(
            Modifier
                .padding(start = if (checked) 21.dp else 3.dp, top = 3.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White),
        )
    }
}

/** Section heading: 15sp bold, the rhythm marker between card groups. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier,
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KC.Ink,
    )
}

/** Uppercase micro-label used above Settings groups. */
@Composable
fun OverlineLabel(text: String) {
    Text(
        text.uppercase(),
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        color = KC.Muted, letterSpacing = 0.65.sp,
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier,
        fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
    )
}

/** List row inside a [KCard]: badge, title/subtitle, optional trailing slot. */
@Composable
fun KRow(
    title: String,
    subtitle: String? = null,
    icon: String? = null,
    iconTint: Color = KC.Coral,
    iconBg: Color = KC.CoralBg,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) IconBadge(icon, iconTint, iconBg, size = 36, corner = 10, iconSize = 20)
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = KC.Ink, fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle, color = KC.Muted, fontFamily = Sans, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
    }
}

/** Checkbox-style ring used by the Today checklist and vaccine dose lists. */
@Composable
fun CheckRing(checked: Boolean, rounded: Boolean = false, size: Int = 24) {
    val shape = if (rounded) RoundedCornerShape(8.dp) else RoundedCornerShape(percent = 50)
    Box(
        Modifier
            .size(size.dp)
            .border(2.dp, if (checked) KC.Coral else KC.CoralPale, shape)
            .clip(shape)
            .background(if (checked) KC.Coral else KC.Surface),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(KIcons["check"], null, tint = Color.White, modifier = Modifier.size((size * 2 / 3).dp))
        }
    }
}
