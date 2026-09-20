package com.kilkari.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import kotlin.math.max
import kotlin.math.roundToInt

/** How wide the saved portrait is. Large enough for the biggest place it is drawn, no larger. */
private const val OUTPUT_PX = 720

/** Zooming past this turns a phone photo into porridge. */
private const val MAX_ZOOM = 6f

/** Decoded no bigger than this, so a 12-megapixel photo does not have to fit in memory. */
private const val MAX_SOURCE_PX = 2048

/**
 * Frames a picture before it becomes the child's avatar.
 *
 * A phone photo is rarely a square with the face in the middle, and the avatar is a small
 * circle: without this the app decided what to keep, usually a shoulder. Pinch to zoom, drag to
 * move, and what is inside the circle is what is saved.
 */
@Composable
fun ImageCropDialog(
    source: Uri,
    onCancel: () -> Unit,
    onCropped: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(source) { decodeScaled(context, source) }

    if (bitmap == null) {
        // Nothing to frame — a file that will not decode should not trap the parent in a
        // dialog with no picture and no explanation.
        Dialog(onDismissRequest = onCancel) {
            Column(
                Modifier.background(KC.Surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("That image could not be opened.", fontFamily = Sans, fontSize = 14.sp, color = KC.Ink)
                PrimaryButton("Close", onClick = onCancel)
            }
        }
        return
    }

    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var viewport by remember(source) { mutableFloatStateOf(0f) }

    val image = remember(bitmap) { bitmap.asImageBitmap() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(KC.Ink)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Frame the photo",
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                textAlign = TextAlign.Center,
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White,
            )
            Text(
                "Pinch to zoom, drag to move. What is inside the circle is kept.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontFamily = Sans, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clipToBounds()
                    .onSizeChanged { viewport = it.width.toFloat() }
                    .pointerInput(source) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val next = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            val scale = baseScale(viewport, bitmap) * next
                            zoom = next
                            offset = clampOffset(offset + pan, viewport, bitmap, scale)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
                // The circle is drawn over the picture rather than clipping it, so the parts
                // being cut off stay visible while framing.
                androidx.compose.foundation.Canvas(
                    // BlendMode.Clear only cuts through when the layer it draws into has its
                    // own alpha; without this the circle fills black instead of clearing.
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
                ) { drawCircleMask() }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", Modifier.weight(1f), onClick = onCancel)
                Box(Modifier.weight(1f)) {
                    PrimaryButton("Use photo") {
                        val cropped = cropToSquare(context, bitmap, viewport, zoom, offset)
                        if (cropped != null) onCropped(cropped) else onCancel()
                    }
                }
            }
        }
    }
}

/** The scale at which the picture just covers a square viewport, before the user's zoom. */
private fun baseScale(viewport: Float, bitmap: Bitmap): Float {
    if (viewport <= 0f) return 1f
    return max(viewport / bitmap.width, viewport / bitmap.height)
}

/** Keeps the picture covering the viewport, so no gap can be dragged into the circle. */
private fun clampOffset(raw: Offset, viewport: Float, bitmap: Bitmap, scale: Float): Offset {
    val slackX = ((bitmap.width * scale) - viewport).coerceAtLeast(0f) / 2f
    val slackY = ((bitmap.height * scale) - viewport).coerceAtLeast(0f) / 2f
    return Offset(raw.x.coerceIn(-slackX, slackX), raw.y.coerceIn(-slackY, slackY))
}

/** Dims everything outside the circle that will be kept. */
private fun DrawScope.drawCircleMask() {
    val radius = size.minDimension / 2f
    drawRect(color = Color.Black.copy(alpha = 0.55f), size = size)
    drawCircle(color = Color.Transparent, radius = radius, blendMode = BlendMode.Clear)
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
}

/**
 * Turns what is on screen into a square image.
 *
 * The viewport is mapped back into the source picture's own pixels, so the crop is taken at
 * the photo's resolution rather than at the size it happened to be displayed.
 */
private fun cropToSquare(
    context: Context,
    bitmap: Bitmap,
    viewport: Float,
    zoom: Float,
    offset: Offset,
): Uri? {
    if (viewport <= 0f) return null
    val scale = baseScale(viewport, bitmap) * zoom
    val visible = viewport / scale

    val left = ((bitmap.width - visible) / 2f) - (offset.x / scale)
    val top = ((bitmap.height - visible) / 2f) - (offset.y / scale)

    val size = visible.roundToInt().coerceAtLeast(1).coerceAtMost(minOf(bitmap.width, bitmap.height))
    val x = left.roundToInt().coerceIn(0, bitmap.width - size)
    val y = top.roundToInt().coerceIn(0, bitmap.height - size)

    return runCatching {
        val square = Bitmap.createBitmap(bitmap, x, y, size, size)
        val out = if (size > OUTPUT_PX) {
            Bitmap.createScaledBitmap(square, OUTPUT_PX, OUTPUT_PX, true)
        } else {
            square
        }
        val target = newFileUri(context, "photos", "portrait")
        context.contentResolver.openOutputStream(target)?.use { stream ->
            out.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        } ?: return null
        target
    }.getOrNull()
}

/** Decoded at a workable size: a full-resolution phone photo is far more than this needs. */
private fun decodeScaled(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SOURCE_PX) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}.getOrNull()
