package com.kilkari.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.LocalAccent
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.springPress
import com.kilkari.ui.theme.rememberPressSource
import com.kilkari.ui.theme.clay
import com.kilkari.ui.theme.KDepth
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
    corner: Int = KDepth.CARD,
    background: Color = KC.Surface,
    border: Color? = KC.Border,
    /** Lifts the card off the cream. Off for cards drawn inside another raised surface. */
    raised: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner.dp)
    val press = rememberPressSource()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // A pressed card squashes slightly, then springs back: the whole app is built out
            // of these, so the feel of one tap is the feel of the product.
            .let { if (onClick != null) it.springPress(press) else it }
            .let { if (raised) it.clay(corner) else it }
            .clip(shape)
            .let {
                if (onClick == null) it
                else it.clickable(interactionSource = press, indication = null, onClick = onClick)
            },
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
    corner: Int = KDepth.HERO,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner.dp)
    val press = rememberPressSource()
    Box(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.springPress(press) else it }
            // Lifted further than a plain card, and tinted with its own colour so the glow
            // under it belongs to the card rather than sitting behind it.
            .clay(corner, KDepth.heroElevation, colors.first())
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .let {
                if (onClick == null) it
                else it.clickable(interactionSource = press, indication = null, onClick = onClick)
            },
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/**
 * A circular icon button on a tinted ground — the header actions. Sized to the 48dp touch
 * minimum even though the circle itself is smaller.
 */
@Composable
fun RoundIconButton(
    icon: String,
    tint: Color,
    background: Color,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val press = rememberPressSource()
    Box(
        Modifier
            .size(48.dp)
            .springPress(press)
            .clip(CircleShape)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KIcons[icon], contentDescription, tint = tint, modifier = Modifier.size(21.dp))
        }
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
        // A reminder may carry an emoji instead of a drawn icon. Emoji are already coloured,
        // so they are painted as text at the size the icon would have occupied.
        if (KIcons.isDrawn(icon)) {
            Icon(KIcons[icon], contentDescription = null, tint = tint, modifier = Modifier.size(iconSize.dp))
        } else {
            Text(icon, fontSize = (iconSize - 3).sp, lineHeight = (iconSize - 3).sp)
        }
    }
}

/**
 * The child's picture, round, falling back to their initial until one is taken.
 *
 * Sized by [modifier] rather than a fixed dimension, so it can take a share of a row as
 * readily as a fixed diameter; the monogram's letter scales to whatever box it lands in.
 * [ring] draws the border that keeps the circle legible against a coloured card.
 */
@Composable
fun ChildAvatar(
    photoUri: String?,
    name: String,
    modifier: Modifier = Modifier.size(44.dp),
    ring: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val bitmap = remember(photoUri) { photoUri?.let { loadAvatar(context, it) } }
    val shape = RoundedCornerShape(percent = 50)

    BoxWithConstraints(
        modifier
            .clip(shape)
            .let { if (ring != null) it.border(2.dp, ring, shape) else it }
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$name's photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(listOf(KC.CoralLight, KC.GoldLight)))
            )
            Text(
                name.take(1).uppercase(),
                color = Color.White,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (maxWidth.value / 2.4f).sp,
            )
        }
    }
}

/** Decoded small: the avatar is never drawn bigger than a few dozen dp. */
private fun loadAvatar(context: Context, uri: String): ImageBitmap? = runCatching {
    context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
    }
}.getOrNull()

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
    val press = rememberPressSource()
    val accent = LocalAccent.current
    Surface(
        modifier = modifier
            .height(36.dp)
            .springPress(press)
            .clip(RoundedCornerShape(999.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent.main else KC.Surface,
        border = BorderStroke(1.dp, if (selected) accent.main else accent.ring),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else accent.deep,
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
        }
    }
}

/** Segmented control on a lilac track — Feed type, expense category. */
@Composable
fun KSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(accent.bg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            // The pill fades between options rather than jumping, which at this size reads as
            // the selection moving.
            val fill by animateColorAsState(
                targetValue = if (on) accent.main else Color.Transparent,
                animationSpec = tween(200),
                label = "segment",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Color.White else accent.deep,
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
    val press = rememberPressSource()
    val accent = LocalAccent.current
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .springPress(press)
            .let { if (enabled) it.clay(999, 8.dp, accent.main) else it }
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) accent.main else accent.ring)
            .clickable(enabled = enabled, interactionSource = press, indication = null, onClick = onClick),
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
    val accent = LocalAccent.current
    Surface(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = KC.Surface,
        border = BorderStroke(1.dp, accent.ring),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(KIcons[icon], null, tint = accent.deep, modifier = Modifier.size(20.dp))
            Text(label, color = accent.deep, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** iOS-style switch drawn to the design's 44×26 spec. */
@Composable
fun KSwitch(checked: Boolean, onToggle: (() -> Unit)? = null) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) accent.main else KC.Track)
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
        fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp,
        letterSpacing = (-0.3).sp, color = KC.Ink,
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
    // Onboarding leans on these lines to explain each step, so they are sized as prose with a
    // 1.5 line height rather than as a caption squeezed under a heading.
    Text(
        text, modifier = modifier,
        fontFamily = Sans, fontSize = 14.sp, lineHeight = 21.sp, color = KC.Muted,
    )
}

/** List row inside a [KCard]: badge, title/subtitle, optional trailing slot. */
@Composable
fun KRow(
    title: String,
    subtitle: String? = null,
    icon: String? = null,
    iconTint: Color = LocalAccent.current.deep,
    iconBg: Color = LocalAccent.current.bg,
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
fun CheckRing(checked: Boolean, rounded: Boolean = false, size: Int = 24, glyph: String? = null) {
    val accent = LocalAccent.current
    val shape = if (rounded) RoundedCornerShape(8.dp) else RoundedCornerShape(percent = 50)
    Box(
        Modifier
            .size(size.dp)
            .border(2.dp, if (checked) accent.main else accent.ring, shape)
            .clip(shape)
            .background(if (checked) accent.main else KC.Surface),
        contentAlignment = Alignment.Center,
    ) {
        val inner = (size * 2 / 3).dp
        when {
            // Ticked: the state matters more than what the row is.
            checked ->
                Icon(KIcons["check"], null, tint = Color.White, modifier = Modifier.size(inner))
            // Still to do: the ring stays the tap target, and carries the chosen icon so the
            // row is recognisable at a glance rather than being one of several bare circles.
            glyph == null -> Unit
            KIcons.isDrawn(glyph) ->
                Icon(KIcons[glyph], null, tint = accent.main, modifier = Modifier.size(inner))
            else -> Text(glyph, fontSize = (size / 2).sp, lineHeight = (size / 2).sp)
        }
    }
}
