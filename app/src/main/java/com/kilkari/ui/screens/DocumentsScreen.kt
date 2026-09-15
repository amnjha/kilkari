package com.kilkari.ui.screens

import android.content.Context
import android.net.Uri
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
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KFab
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.sheets.DocumentSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.io.File

/**
 * Camera scans filed by date. Pages are captured straight into app-private storage, so
 * nothing lands in the shared gallery.
 */
@Composable
fun DocumentsScreen(vm: KilkariViewModel, go: NavActions) {
    val documents by vm.documents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetOpen by remember { mutableStateOf(false) }
    val pages = remember { mutableStateListOf<String>() }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { pages += it.toString() }
        pendingUri = null
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
                    .padding(top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Scanned and stored on this phone, filed by date.",
                    fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                )

                if (documents.isEmpty()) {
                    KCard {
                        Text(
                            "No documents yet. Tap Scan to photograph a certificate or prescription.",
                            modifier = Modifier.padding(14.dp),
                            fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                        )
                    }
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

        KFab("document_scanner", "Scan") {
            pages.clear()
            scanPage()
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false; pages.clear() }) {
            DocumentSheet(pages.size) { title, tags, filedOn ->
                vm.addDocument(title, tags, pages.toList(), filedOn)
                pages.clear()
                sheetOpen = false
            }
            Text(
                "Add another page",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(KC.CoralBg)
                    .clickable { sheetOpen = false; scanPage() }
                    .padding(vertical = 12.dp),
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = KC.CoralDeep,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Renders a captured page, falling back to a placeholder when the file is missing. */
@Composable
internal fun PagePreview(uri: String, height: Int) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(uri) { loadThumbnail(context, uri) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(KC.ClayBg),
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
            Icon(
                KIcons["document_scanner"], null,
                tint = KC.ClayDeep, modifier = Modifier.size(28.dp),
            )
        }
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
