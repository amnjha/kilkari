package com.kilkari.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.repo.PHOTO_DIR
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.components.rememberImageSource
import com.kilkari.ui.sheets.PhotoCheckInSheet
import com.kilkari.ui.sheets.AlbumSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/** Links out to Google Photos albums. Kilkari stores the link, never the photos. */
@Composable
fun PhotosScreen(vm: KilkariViewModel, go: NavActions) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetOpen by remember { mutableStateOf(false) }

    val baby by vm.baby.collectAsStateWithLifecycle()
    // The weekly check-in is its own two-step flow; the + button adds an album on its own.
    var checkInOpen by remember { mutableStateOf(false) }
    val photo = rememberImageSource(PHOTO_DIR, "portrait") { uri ->
        vm.setChildPhoto(uri.toString())
    }

    val pendingEntry by vm.pendingEntry.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEntry) {
        if (pendingEntry == "album") {
            checkInOpen = true
            vm.consumeEntry()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Photo albums", go::back) {
                IconButton44("add_link", KC.Coral, { sheetOpen = true }, iconSize = 26)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Albums live in Google Photos — Kilkari just keeps the links.",
                    fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                )

                if (albums.isEmpty()) {
                    KCard {
                        Text(
                            "No albums linked yet.",
                            modifier = Modifier.padding(14.dp),
                            fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                        )
                    }
                }

                albums.forEach { album ->
                    KCard(onClick = { openLink(context, album.url) }) {
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .width(110.dp)
                                    .height(90.dp)
                                    .background(KC.ClayBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    KIcons["photo_library"], null,
                                    tint = KC.ClayDeep, modifier = Modifier.size(26.dp),
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    album.title, fontFamily = Sans, fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp, color = KC.Ink,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (album.subtitle.isNotBlank()) {
                                    Text(
                                        album.subtitle, fontFamily = Sans, fontSize = 12.sp,
                                        color = KC.Muted, modifier = Modifier.padding(top = 2.dp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(
                                    Modifier.padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        KIcons["open_in_new"], null,
                                        tint = KC.Coral, modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "Open in Google Photos",
                                        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp, color = KC.Coral,
                                    )
                                }
                            }
                            Text(
                                "Remove",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .clickable { vm.deleteAlbum(album) },
                                fontFamily = Sans, fontSize = 12.sp, color = KC.Faint,
                            )
                        }
                    }
                }
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            AlbumSheet { title, subtitle, url ->
                vm.addAlbum(title, subtitle, url)
                sheetOpen = false
            }
        }

        KSheet(checkInOpen, onDismiss = { checkInOpen = false }) {
            PhotoCheckInSheet(
                childName = baby?.name ?: "your baby",
                photoUri = baby?.photoUri,
                onCamera = photo::camera,
                onGallery = photo::gallery,
                onSaveAlbum = { title, url -> vm.addAlbum(title, "", url) },
                onDone = {
                    // Either step may have been skipped; reaching the end is what counts as
                    // having dealt with the week's prompt.
                    vm.completePhotoCheckIn()
                    checkInOpen = false
                },
            )
        }
    }
}
