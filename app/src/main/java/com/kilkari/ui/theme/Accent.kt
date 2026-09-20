package com.kilkari.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One screen's colour, as a set rather than a single hue.
 *
 * Before this, every control in the app was coral: the chips, the switches, the buttons, the
 * tick rings, the selected tab. A parent moving from the day's log to the vaccine list to the
 * fund saw the same screen wearing different words. An [Accent] is handed down the tree
 * instead, and the shared controls take their colour from it, so a screen changes hue as a
 * whole and moving between them feels like moving somewhere.
 *
 * Every [main] carries white text at 4.5:1 or better and every [deep] clears 4.5:1 as text on
 * the cream ground — checked, not guessed. Gold and clay are the two families whose mid step
 * is too light for white text, so their accents start at the deep step and stay there.
 */
@Immutable
data class Accent(
    /** Fills that carry white text: the primary button, the selected chip, the active tab. */
    val main: Color,
    /** Coloured text and icons, on white or on one of the pale grounds. */
    val deep: Color,
    /** Decoration only — never behind text. */
    val light: Color,
    /** The palest ground, for an icon badge behind a 20dp glyph. */
    val bg: Color,
    /** The ground a whole tile or header is painted with, and unmistakably coloured. */
    val wash: Color,
    /** Borders, rings and tracks. */
    val ring: Color,
) {
    /** Deep → main, top-left to bottom-right: the gradient for a hero card in this hue. */
    val gradient: List<Color> get() = listOf(deep, main)

}

/**
 * The hue each part of the app answers to.
 *
 * Grouped by what a parent is doing rather than by screen, so two screens about the same
 * thing — Growth and the measurement sheet, Photos and the timeline — agree.
 */
object KAccents {

    /** The brand, the home screen, and anything clinical or urgent. */
    val Brand = Accent(KC.Coral, KC.CoralDeep, KC.CoralLight, KC.CoralBg, KC.CoralWash, KC.CoralRing)

    /** Logging the day: feeds, sleep, nappies. The calm half. */
    val Day = Accent(KC.Sky, KC.SkyDeep, KC.SkyLight, KC.SkyBg, KC.SkyWash, KC.SkyRing)

    /** Health: vaccines, appointments, doctors. */
    val Health = Accent(KC.Teal, KC.TealDeep, KC.TealLight, KC.TealBg, KC.TealWash, KC.TealRing)

    /** Anything that grows or completes: measurements, percentiles, teeth coming in. */
    val Growth = Accent(KC.Leaf, KC.LeafDeep, KC.LeafLight, KC.LeafBg, KC.LeafWash, KC.LeafRing)

    /** Medicines and doses. Warm, but not the brand's warm. */
    val Care = Accent(KC.ClayDeep, KC.ClayDeep, KC.ClayLight, KC.ClayBg, KC.ClayWash, KC.ClayBg2)

    /** The fund: expenses, contributions, investments. */
    val Money = Accent(KC.GoldDeep, KC.GoldDeep, KC.GoldLight, KC.GoldBg, KC.GoldWash, KC.GoldRing)

    /** Keepsakes: the timeline, albums, birthdays, first everythings. */
    val Memories = Accent(KC.Rose, KC.RoseDeep, KC.RoseLight, KC.RoseBg, KC.RoseWash, KC.RoseRing)

    /** Paperwork and records — the drawer of the app. */
    val Records = Accent(KC.Sea, KC.SeaDeep, KC.SeaLight, KC.SeaBg, KC.SeaWash, KC.SeaRing)

    /** Reminders, insights, settings: the app talking about itself. */
    val Quiet = Accent(KC.Lilac, KC.LilacDeep, KC.LilacLight, KC.LilacBg, KC.LilacWash, KC.LilacRing)
}

/** Read by every shared control, so a screen only has to say its colour once. */
val LocalAccent = staticCompositionLocalOf { KAccents.Brand }

/** Paints everything inside in [accent]. One line at the top of a screen. */
@Composable
fun AccentScope(accent: Accent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides accent, content = content)
}

/**
 * The band of colour a screen opens with.
 *
 * Painted behind the container rather than added to it, so it costs no layout and works the
 * same whether the screen is a Box, a scrolling Column, or a Column with a title bar on top.
 * The height is fixed in dp and the gradient reaches the cream inside it, so on a tall phone
 * the colour still stops where the content begins instead of stretching down the page.
 *
 * [height] is measured from the first line of content, and the status bar is added on top of
 * it. The band is drawn by whatever sits behind that inset — so the clock and the battery sit
 * on the screen's colour, and the colour runs off the top of the display rather than starting
 * at a line underneath it.
 */
@Composable
fun Modifier.headerWash(
    accent: Accent = LocalAccent.current,
    height: Dp = 230.dp,
): Modifier {
    // A third strength. [Accent.wash] is mixed for a tile the size of a thumb; the band is the
    // largest piece of colour on any screen, and at anything like full strength the screen
    // becomes the colour rather than wearing it.
    val tint = accent.wash.copy(alpha = 0.32f)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val total = height + statusBar
    return this.drawBehind {
        val band = total.toPx()
        drawRect(KC.Screen, size = Size(size.width, band))
        drawRect(
            brush = Brush.verticalGradient(listOf(tint, Color.Transparent), startY = 0f, endY = band),
            size = Size(size.width, band),
        )
    }
}

/**
 * This colour mixed [amount] of the way out of the cream ground, as an opaque colour.
 *
 * Softening a tint with alpha looks right until the surface wearing it is raised: a card's
 * shadow is drawn behind it, and a see-through fill lets that shadow bleed up through the
 * middle, so a flat tint reads as a dirty gradient with a halo round the edge. Mixing the two
 * colours and handing over the result keeps the card opaque and the tint flat.
 */
fun Color.onCream(amount: Float): Color = lerp(KC.Screen, this, amount)
