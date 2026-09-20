package com.kilkari.ui.screens

import android.content.Context
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.Fmt
import com.kilkari.domain.Paperwork
import com.kilkari.domain.PaperworkStatus
import com.kilkari.domain.PaperworkStep
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.Spot
import com.kilkari.ui.components.EmptyState
import com.kilkari.ui.components.rememberImageSource
import com.kilkari.ui.components.rememberFileSource
import com.kilkari.ui.sheets.SheetTitle
import com.kilkari.ui.sheets.SheetHint
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.SourceRow
import com.kilkari.ui.components.KFab
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.sheets.DocumentSheet
import com.kilkari.ui.theme.KDepth
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.io.File

/**
 * Certificates, prescriptions and the rest, filed by date.
 *
 * A page can be photographed, chosen from the gallery, or attached as a file. Everything is
 * copied into app-private storage: a scan never lands in the shared gallery, and a picked
 * file stops depending on a read permission that expires.
 */
@Composable
fun DocumentsScreen(vm: KilkariViewModel, go: NavActions) {
    val documents by vm.documents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetOpen by remember { mutableStateOf(false) }
    val pages = remember { mutableStateListOf<String>() }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    // Opened by the + button; the three ways in all end at the same sheet.
    var chooserOpen by remember { mutableStateOf(false) }

    // The Paperwork screen can send someone here to scan a named document. The title is
    // filled in for them, and filing it records that document as obtained.
    val paperwork by vm.paperwork.collectAsStateWithLifecycle()
    var suggestedTitle by remember { mutableStateOf("") }
    val pendingEntry by vm.pendingEntry.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEntry) {
        val entry = pendingEntry ?: return@LaunchedEffect
        if (entry.startsWith("scan:")) {
            suggestedTitle = Paperwork.byKey(entry.removePrefix("scan:"))?.title.orEmpty()
            pages.clear()
            chooserOpen = true
            vm.consumeEntry()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { pages += it.toString() }
        pendingUri = null
        sheetOpen = true
    }

    // Like the camera, these reopen the sheet on the way back rather than before the picker
    // appears, so the sheet is never sitting behind it.
    val gallery = rememberImageSource("documents", "page") { uri ->
        pages += uri.toString()
        sheetOpen = true
    }
    val files = rememberFileSource("documents", "file") { uri ->
        pages += uri.toString()
        sheetOpen = true
    }

    fun scanPage() {
        val uri = newPageUri(context)
        pendingUri = uri
        camera.launch(uri)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Documents", go::back)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = KDepth.navClearance),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Scans, photos and files, kept on this phone and filed by date.",
                    fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                )

                PaperworkBanner(paperwork) { go.push(Routes.PAPERWORK) }

                if (documents.isEmpty()) {
                    EmptyState(
                        Spot.EMPTY_DOCUMENTS,
                        "Nothing filed yet",
                        "Birth certificate, hospital discharge, insurance — tap + to scan one, " +
                            "pick a photo, or attach a file.",
                    )
                }

                documents.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { doc ->
                            KCard(
                                Modifier.weight(1f), corner = 16,
                                onClick = {
                                    vm.selectDocument(doc.id)
                                    go.push(Routes.DOCUMENT_DETAIL)
                                },
                            ) {
                                PagePreview(
                                    uri = doc.pageUris.split(",").firstOrNull().orEmpty(),
                                    height = 110,
                                )
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        doc.title, fontFamily = Sans, fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp, lineHeight = 17.sp, color = KC.Ink,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${Fmt.date(doc.filedOn)} · ${doc.pageCount} ${Fmt.plural(doc.pageCount.toLong(), "page")}",
                                        fontFamily = Sans, fontSize = 11.sp, color = KC.Muted,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }

        KFab("add", "Add") {
            pages.clear()
            chooserOpen = true
        }

        KSheet(chooserOpen, onDismiss = { chooserOpen = false }) {
            SheetTitle("Add a document")
            SheetHint("Whatever you pick is copied into this app, not left where it was.")
            SourceRow("document_scanner", "Scan a page", "Photograph it with the camera") {
                chooserOpen = false
                scanPage()
            }
            SourceRow("photo_camera", "Choose an image", "From the phone's photos") {
                chooserOpen = false
                gallery.gallery()
            }
            SourceRow("folder", "Choose a file", "A PDF or anything else saved on the phone") {
                chooserOpen = false
                files()
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false; pages.clear(); suggestedTitle = "" }) {
            DocumentSheet(pages.size, initialTitle = suggestedTitle) { title, tags, filedOn ->
                vm.addDocument(title, tags, pages.toList(), filedOn)
                pages.clear()
                suggestedTitle = ""
                sheetOpen = false
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AddMore("Scan", Modifier.weight(1f)) { sheetOpen = false; scanPage() }
                AddMore("Image", Modifier.weight(1f)) { sheetOpen = false; gallery.gallery() }
                AddMore("File", Modifier.weight(1f)) { sheetOpen = false; files() }
            }
        }
    }
}

/**
 * The identity-document chain, in one line: which document is next and how the four stand.
 * Documents is where a parent comes to file a certificate, so the reminder for the next one
 * belongs here as much as under More.
 */
@Composable
private fun PaperworkBanner(steps: List<PaperworkStep>, onClick: () -> Unit) {
    if (steps.isEmpty()) return
    val next = steps.firstOrNull { it.status == PaperworkStatus.ACTIVE }
    val obtained = steps.count { it.status == PaperworkStatus.OBTAINED }
    KCard(corner = 16, background = KC.ClayBg, border = KC.ClayBg2, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(next?.kind?.icon ?: "badge", KC.ClayDeep, KC.Surface, size = 36, corner = 10, iconSize = 20)
            Column(Modifier.weight(1f)) {
                Text(
                    if (next != null) "${next.kind.title} ${Fmt.dueText(next.inDays ?: 0)}"
                    else "Paperwork all in hand",
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = if (next?.overdue == true) KC.Danger else KC.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Paperwork · $obtained of ${steps.size} obtained",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
            Icon(KIcons["chevron_right"], null, tint = KC.ClayDeep, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Renders a page: the image itself where there is one, otherwise what the file is.
 *
 * A PDF has no thumbnail, and an unlabelled placeholder said nothing about what had been
 * attached or how to look at it. [openable] adds the way out to whatever app handles the type.
 */
@Composable
internal fun PagePreview(uri: String, height: Int, openable: Boolean = false) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(uri) { loadThumbnail(context, uri) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(KC.ClayBg)
            .let {
                if (bitmap == null && openable) it.clickable { openFile(context, uri) } else it
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    KIcons["folder"], null,
                    tint = KC.ClayDeep, modifier = Modifier.size(28.dp),
                )
                Text(
                    fileLabel(uri),
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = KC.ClayDeep,
                )
                if (openable) {
                    Text(
                        "Tap to open",
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    )
                }
            }
        }
    }
}

/** "PDF", "DOCX" — the type, taken off the stored file's own name. */
private fun fileLabel(uri: String): String {
    val name = Uri.parse(uri).lastPathSegment.orEmpty().substringAfterLast('/')
    val extension = name.substringAfterLast('.', "")
    return if (extension.isBlank()) "File" else extension.uppercase()
}

/** Hands the file to whatever app handles its type, with read access for the length of the view. */
private fun openFile(context: Context, uri: String) {
    runCatching {
        val parsed = Uri.parse(uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(parsed, context.contentResolver.getType(parsed) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun loadThumbnail(context: Context, uri: String): ImageBitmap? {
    if (uri.isBlank()) return null
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
            android.graphics.BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    }.getOrNull()
}

/** A fresh file under files/documents, exposed through the app's FileProvider. */
internal fun newPageUri(context: Context): Uri {
    val dir = File(context.filesDir, "documents").apply { mkdirs() }
    val file = File(dir, "page_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/** Adding a further page to a document already being filled in. */
@Composable
private fun AddMore(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(KC.CoralBg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        color = KC.CoralDeep,
        textAlign = TextAlign.Center,
    )
}
