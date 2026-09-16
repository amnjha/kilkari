package com.kilkari.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.ui.components.ChildAvatar
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SourceRow
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * The weekly photo check-in: a new picture, then somewhere to keep the rest of the week's.
 *
 * Two steps rather than one screen because they are unrelated jobs — a picture that lives in
 * the app, and a link to an album that does not. Either can be skipped: most weeks a parent
 * wants one or the other, and a prompt that insists on both is a prompt that gets dismissed.
 */
@Composable
fun ColumnScope.PhotoCheckInSheet(
    childName: String,
    photoUri: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSaveAlbum: (title: String, url: String) -> Unit,
    onDone: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    if (step == 0) {
        SheetTitle("This week's photo")
        SheetHint("It becomes $childName's picture on the Today screen.")
        PhotoChooser(photoUri, childName, onCamera, onGallery)
        PrimaryButton("Next") { step = 1 }
        SkipLink("Skip the photo") { step = 1 }
    } else {
        SheetTitle("Album link")
        SheetHint("Kilkari keeps the link — the photos stay in the album.")
        SheetField("Album name", title, "e.g. Week 12") { title = it }
        SheetField("Link", url, "https://photos.app.goo.gl/…") { url = it }
        PrimaryButton("Save link", enabled = url.isNotBlank()) {
            onSaveAlbum(title.trim().ifBlank { "Album" }, url.trim())
            onDone()
        }
        SkipLink("Skip the link") { onDone() }
    }
}

/** Updating the picture on its own, which is what tapping the avatar does. */
@Composable
fun ColumnScope.ChildPhotoSheet(
    childName: String,
    photoUri: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
) {
    SheetTitle("$childName's photo")
    PhotoChooser(photoUri, childName, onCamera, onGallery)
    PrimaryButton("Done") { onDone() }
    if (photoUri != null) SheetDelete("Remove the photo", onRemove)
}

/** The picture as it stands, with the two ways of replacing it. */
@Composable
private fun ColumnScope.PhotoChooser(
    photoUri: String?,
    childName: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ChildAvatar(photoUri, childName, Modifier.size(108.dp))
    }
    if (photoUri == null) {
        Text(
            "No photo yet.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
        )
    }
    // The same two rows a document offers, so choosing a picture reads the same everywhere.
    SourceRow("photo_camera", "Take a photo", "Use the camera now", onClick = onCamera)
    SourceRow("folder", "Choose from gallery", "From the phone's photos", onClick = onGallery)
}

/** The way out of a step that is not compulsory. */
@Composable
private fun ColumnScope.SkipLink(label: String, onSkip: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onSkip)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
        fontFamily = Sans, fontSize = 14.sp, color = KC.Muted,
    )
}
