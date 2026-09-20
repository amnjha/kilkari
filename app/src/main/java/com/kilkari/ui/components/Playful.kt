package com.kilkari.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.provider.Settings
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.KGradients
import com.kilkari.ui.theme.clay

/**
 * The soft, warm ground the welcome screens sit on.
 *
 * Colour is laid down as a gradient with a few wide, low-opacity blooms drifting over it,
 * drawn as radial gradients rather than blurred shapes so they cost nothing and look the same
 * on every phone the app supports. The drift is slow enough to read as light moving rather
 * than as something demanding attention, and it stops entirely when the system asks for
 * reduced motion.
 */
@Composable
fun BlobBackdrop(
    modifier: Modifier = Modifier,
    brush: Brush = KGradients.welcome,
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val drift = if (animated && !reducedMotion()) {
        val transition = rememberInfiniteTransition(label = "blobs")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "drift",
        ).value
    } else {
        0.5f
    }

    Box(modifier.background(brush)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            bloom(KC.LilacLight.copy(alpha = 0.30f), Offset(w * 0.18f, h * (0.12f + drift * 0.04f)), w * 0.55f)
            bloom(KC.CoralLight.copy(alpha = 0.22f), Offset(w * 0.92f, h * (0.30f - drift * 0.05f)), w * 0.50f)
            bloom(KC.GoldLight.copy(alpha = 0.20f), Offset(w * 0.20f, h * (0.82f + drift * 0.03f)), w * 0.60f)
        }
        content()
    }
}

/** One soft bloom of colour, fading to nothing at its edge. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.bloom(
    color: Color,
    centre: Offset,
    radius: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/**
 * The launcher artwork as a portrait: a white squircle lifted off the ground, the way the
 * reference designs frame their illustrations.
 */
@Composable
fun BlobPortrait(painter: Painter, size: Int = 220, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .clay(corner = size / 2, elevation = 22.dp, tint = KC.LilacLight)
            .clip(RoundedCornerShape((size * 0.44f).dp))
            .background(
                Brush.linearGradient(listOf(Color.White, KC.LilacBg))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding((size * 0.06f).dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/** True when the phone is set to reduce animations, which this app takes at its word. */
@Composable
private fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
