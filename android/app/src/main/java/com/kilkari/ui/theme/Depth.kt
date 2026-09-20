package com.kilkari.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The soft, slightly inflated feel the app is drawn with.
 *
 * Everything a parent taps is a rounded, raised thing rather than a rectangle with a hairline:
 * shadows are tinted with the surface's own hue instead of black, so a card looks lit rather
 * than outlined, and a press squashes it a little the way a real button would. The point is
 * warmth — this is an app used one-handed at 4am, and it should feel friendly rather than
 * clinical — not decoration for its own sake.
 */
object KDepth {

    /** Card, tile and sheet corner radii. Generous, and consistent everywhere. */
    const val CARD = 24
    const val TILE = 26
    const val HERO = 30
    const val CHIP = 999

    /** How far each surface lifts off the cream. */
    val restingElevation = 10.dp
    val heroElevation = 18.dp

    /**
     * How far a scrolling screen has to clear the bottom of the window.
     *
     * The navigation bar floats over the content rather than sitting in a strip of its own,
     * so the last card would otherwise end up underneath it. The bar is about 86dp with its
     * margins, and this leaves enough that the final row is comfortably clear rather than
     * touching. Screens use this instead of each guessing a number.
     */
    val navClearance = 104.dp
}

/**
 * A soft raised surface: the shadow is tinted rather than grey, so it reads as warmth under
 * the card instead of a drop shadow behind it.
 */
fun Modifier.clay(
    corner: Int = KDepth.CARD,
    elevation: androidx.compose.ui.unit.Dp = KDepth.restingElevation,
    tint: Color = KC.ClayShadow,
): Modifier = this.shadow(
    elevation = elevation,
    shape = RoundedCornerShape(corner.dp),
    clip = false,
    ambientColor = tint,
    spotColor = tint,
)

/**
 * The squish. Scales to 96% while held, on a spring rather than a curve, so a tap feels like
 * pressing something soft. Layout is untouched — only the drawing scales — so nothing around
 * it moves.
 */
@Composable
fun Modifier.springPress(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "press",
    )
    return this.scale(scale)
}

/** An interaction source that lives for as long as the composable that presses it. */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** The gradients the playful surfaces are painted with. */
object KGradients {

    /** The welcome ground: cream warming into lilac, as a sunrise rather than a wash. */
    val welcome = Brush.linearGradient(
        colors = listOf(Color(0xFFFFF7EE), Color(0xFFF6EFFB), Color(0xFFEFEBFD)),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )

    /** Behind the home screen's header, fading into the page. */
    val header = Brush.verticalGradient(listOf(Color(0xFFFDF3E9), KC.Screen))

    /** The brand card. */
    val coral = listOf(KC.CoralDeep, KC.Clay)

    /** The gentler counterpart, for anything that is not asking something of the parent. */
    val lilac = listOf(KC.LilacDeep, KC.Lilac)
}
