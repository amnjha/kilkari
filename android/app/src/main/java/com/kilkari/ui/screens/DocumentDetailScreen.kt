package com.kilkari.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.sheets.DocumentSheet
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SecondaryButton
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.theme.KDepth
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.io.File

/** A filed scan: its pages, metadata, and share / export actions. */
@Composable
fun DocumentDetailScreen(vm: KilkariViewModel, go: NavActions) {
    val doc by vm.selectedDocument.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val d = doc ?: return

    val pages = d.pageUris.split(",").filter { it.isNotBlank() }
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        DetailBar(d.title, go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = KDepth.navClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            pages.forEachIndexed { i, uri ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
                    PagePreview(uri, height = 380, openable = true)
                }
                Text(
                    "Page ${i + 1} of ${pages.size}",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }

            KCard(corner = 16) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetaRow("Filed", Fmt.dateFull(d.filedOn))
                    MetaRow("Pages", "${d.pageCount}")
                    MetaRow("Tags", d.tags.ifBlank { "—" })
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Edit", Modifier.weight(1f), icon = "edit") { editing = true }
                SecondaryButton("Share", Modifier.weight(1f), icon = "share") {
                    sharePages(context, pages)
                }
                Box(Modifier.weight(1f)) {
                    PrimaryButton("Delete") {
                        deletePages(context, pages)
                        vm.deleteDocument(d)
                        go.back()
                    }
                }
            }
            Text(
                "Sharing sends the page images through Android's share sheet — " +
                    "pick a PDF app there to combine them.",
                fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
            )
        }
    }

    // A filed document's name, tags and date are the parts that get typed in a hurry with a
    // baby on one arm; the pages themselves are what they are.
    KSheet(editing, onDismiss = { editing = false }) {
        DocumentSheet(pageCount = d.pageCount, existing = d) { title, tags, filedOn ->
            vm.updateDocument(d, title, tags, filedOn)
            editing = false
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
        Text(value, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KC.Ink)
    }
}

private fun sharePages(context: Context, pages: List<String>) {
    if (pages.isEmpty()) return
    val uris = ArrayList(pages.map(Uri::parse))
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/jpeg"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, "Share document")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Page files live in app-private storage under files/documents, so deleting the record
 * deletes the images too rather than leaving orphans behind.
 */
private fun deletePages(context: Context, pages: List<String>) {
    val dir = File(context.filesDir, "documents")
    pages.forEach { uri ->
        runCatching {
            val name = Uri.parse(uri).lastPathSegment?.substringAfterLast('/') ?: return@forEach
            File(dir, name).takeIf { it.exists() }?.delete()
        }
    }
}
