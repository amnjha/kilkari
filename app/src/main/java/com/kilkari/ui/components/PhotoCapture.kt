package com.kilkari.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Getting an image into the app, from the camera or from what is already on the phone.
 *
 * Everything ends up in app-private storage under a named folder. A picked file has to be
 * copied rather than referenced: the read permission the picker grants is temporary, so a
 * stored content:// URI from the gallery stops resolving, usually much later and silently.
 */

/** A fresh file in [folder] under files/, exposed through the app's FileProvider. */
fun newFileUri(context: Context, folder: String, prefix: String, extension: String = "jpg"): Uri {
    val dir = File(context.filesDir, folder).apply { mkdirs() }
    val file = File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/**
 * Copies [source] into app-private storage and returns the copy's URI, or null if it could
 * not be read. [extension] keeps the original type so a PDF stays openable as a PDF.
 */
fun copyIntoApp(
    context: Context,
    source: Uri,
    folder: String,
    prefix: String,
    extension: String = "jpg",
): Uri? = runCatching {
    val target = newFileUri(context, folder, prefix, extension)
    context.contentResolver.openInputStream(source)?.use { input ->
        context.contentResolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
    } ?: return null
    target
}.getOrNull()

/**
 * Deletes a file this app wrote under [folder], and ignores anything else.
 *
 * Capturing or picking writes a file before it is framed, so both finishing and abandoning a
 * crop leave an intermediate behind; without this every attempt would add one.
 */
fun deleteOwnFile(context: Context, uri: Uri, folder: String) {
    runCatching {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return
        File(File(context.filesDir, folder), name).takeIf { it.exists() }?.delete()
    }
}

/** The file extension behind a content URI, so a copy keeps the original's type. */
fun extensionOf(context: Context, uri: Uri): String {
    val fromType = context.contentResolver.getType(uri)
        ?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    return fromType?.takeIf { it.isNotBlank() } ?: "bin"
}

/** The name a picked file was stored under, for showing what was attached. */
fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
    }
}.getOrNull()

/** Camera and gallery, both landing on the same callback with a URI the app owns. */
@Stable
class ImageSource internal constructor(
    private val takePhoto: () -> Unit,
    private val pickImage: () -> Unit,
) {
    fun camera() = takePhoto()
    fun gallery() = pickImage()
}

/**
 * Wires up the camera and photo-picker launchers for one purpose.
 *
 * [folder] is the subdirectory of files/ the image lands in, so portraits and document pages
 * stay apart.
 */
@Composable
fun rememberImageSource(folder: String, prefix: String, onImage: (Uri) -> Unit): ImageSource {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending?.let(onImage)
        pending = null
    }
    // The photo picker needs no storage permission and shows only what the user selects.
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        picked?.let { copyIntoApp(context, it, folder, prefix)?.let(onImage) }
    }

    return remember(folder, prefix, onImage) {
        ImageSource(
            takePhoto = {
                val uri = newFileUri(context, folder, prefix)
                pending = uri
                camera.launch(uri)
            },
            pickImage = {
                gallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        )
    }
}

/**
 * Picks a file already on the phone — a PDF a clinic emailed, a discharge summary — and copies
 * it in under its own extension so it stays openable as whatever it is.
 *
 * Separate from [rememberImageSource] because this one deliberately accepts anything: the
 * photo picker only offers images, and a prescription is as often a PDF.
 */
@Composable
fun rememberFileSource(folder: String, prefix: String, onFile: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked ->
        picked?.let {
            copyIntoApp(context, it, folder, prefix, extensionOf(context, it))?.let(onFile)
        }
    }
    return remember(folder, prefix, onFile) {
        { picker.launch(arrayOf("application/pdf", "image/*", "text/*")) }
    }
}
