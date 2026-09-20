package com.kilkari.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.DueTask
import com.kilkari.domain.DueTaskKind
import com.kilkari.domain.PaperworkStatus
import com.kilkari.ui.DueTaskBuilder
import com.kilkari.data.db.BabyEntity
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.ChildAvatar
import com.kilkari.ui.components.ImageCropDialog
import com.kilkari.ui.components.deleteOwnFile
import com.kilkari.ui.components.rememberImageSource
import com.kilkari.ui.sheets.ChildPhotoSheet
import com.kilkari.ui.sheets.FeedSheet
import com.kilkari.ui.sheets.SleepSheet
import com.kilkari.ui.sheets.DiaperSheet
import com.kilkari.ui.sheets.BabySheet
import com.kilkari.domain.Fmt
import com.kilkari.domain.LogKind
import com.kilkari.data.repo.PHOTO_DIR
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.CheckRing
import com.kilkari.ui.components.GradientCard
import com.kilkari.ui.components.bleedHorizontal
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.RoundIconButton
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.Monogram
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.theme.headerWash
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.LocalAccent
import com.kilkari.ui.theme.onCream
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Three takes on the home screen, chosen in Settings:
 * A · Agenda (default), B · Hero, C · Checklist. All three read the same underlying state.
 */
@Composable
fun TodayScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var photoSheet by remember { mutableStateOf(false) }
    // The quick-action cards log from here rather than sending you to the Log tab to do it.
    var quickLog by remember { mutableStateOf<LogKind?>(null) }
    val openSleep by vm.openSleep.collectAsStateWithLifecycle()
    val b = baby ?: return

    // Straight to framing rather than straight to the avatar: a phone photo is rarely a
    // square with the face in the middle.
    val context = LocalContext.current
    var cropping by remember { mutableStateOf<Uri?>(null) }
    val photo = rememberImageSource(PHOTO_DIR, "portrait") { uri -> cropping = uri }

    // A wash of the screen's own colour behind the header, fading into the cream — the page
    // starts with colour rather than with a wall of cards.
    Box(Modifier.fillMaxSize().headerWash(height = 260.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val editDetails = { editing = true }
            when (settings.todayVariant) {
                "B" -> TodayHero(vm, go, b.name, b.dob, editDetails)
                "C" -> TodayChecklist(vm, go, b.dob)
                else -> TodayAgenda(
                    vm = vm,
                    go = go,
                    name = b.name,
                    dob = b.dob,
                    photoUri = b.photoUri,
                    onEditPhoto = { photoSheet = true },
                    onQuickLog = { quickLog = it },
                )
            }
        }

        KSheet(editing, onDismiss = { editing = false }) {
            BabySheet(b, settings.metricUnits) { name, dob, sex, place, weight, length, head ->
                vm.updateBabyDetails(name, dob, sex, place, weight, length, head)
                editing = false
            }
        }

        KSheet(quickLog != null, onDismiss = { quickLog = null }) {
            when (quickLog) {
                LogKind.FEED -> FeedSheet { type, side, amount, at ->
                    vm.logFeed(type, side, amount, at); quickLog = null
                }
                LogKind.SLEEP -> SleepSheet(
                    asleepSince = openSleep?.startAt,
                    onStart = { place, from, to -> vm.logSleepStart(place, from, to); quickLog = null },
                    onEnd = { at -> vm.logSleepEnd(at); quickLog = null },
                )
                LogKind.DIAPER -> DiaperSheet { kind, at ->
                    vm.logDiaper(kind, at); quickLog = null
                }
                else -> Unit
            }
        }


        cropping?.let { source ->
            ImageCropDialog(
                source = source,
                // The captured or picked file has served its purpose either way; only the
                // framed result is worth keeping.
                onCancel = {
                    deleteOwnFile(context, source, PHOTO_DIR)
                    cropping = null
                },
                onCropped = { framed ->
                    vm.setChildPhoto(framed.toString())
                    deleteOwnFile(context, source, PHOTO_DIR)
                    cropping = null
                },
            )
        }

        KSheet(photoSheet, onDismiss = { photoSheet = false }) {
            ChildPhotoSheet(
                childName = b.name,
                photoUri = b.photoUri,
                onCamera = photo::camera,
                onGallery = photo::gallery,
                onRemove = { vm.setChildPhoto(null); photoSheet = false },
                onDone = { photoSheet = false },
            )
        }
    }
}

// ── Variant A · Agenda ──────────────────────────────────────────────────────

@Composable
private fun TodayAgenda(
    vm: KilkariViewModel,
    go: NavActions,
    name: String,
    dob: LocalDate,
    photoUri: String?,
    onEditPhoto: () -> Unit,
    onQuickLog: (LogKind) -> Unit,
) {
    val nextVac by vm.nextVaccine.collectAsStateWithLifecycle()
    val latest by vm.latestPerKind.collectAsStateWithLifecycle()
    val openSleep by vm.openSleep.collectAsStateWithLifecycle()

    // Who this is, before what is due: the name, and how old they are today. It used to open
    // with "Good evening", said to a four-week-old who cannot read it, and briefly with a
    // "Tracking" label above the name — but the name is already the answer to what this
    // screen is about, and a word over it only repeated the app. The face belongs to the card
    // below, where it is large enough to be a picture of somebody rather than a bullet point.
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val accent = LocalAccent.current
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp,
                color = KC.Ink, letterSpacing = (-0.5).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Fmt.age(dob)} old today",
                fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                color = KC.MutedStrong,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // White rather than a tinted ground: the header is already a wash of the screen's
        // colour, and a second circle of a near-identical tint on top of it reads as a smudge.
        RoundIconButton("notifications", accent.deep, KC.Surface, "Reminders") { go.push(Routes.REMINDERS) }
    }

    // What is happening now outranks what is due later. A nap started ten minutes ago wants
    // ending; the next vaccine may be six months off and can wait for the card below it. And
    // when neither is true the card stays, saying so: a parent checking at 4am wants to be
    // told there is nothing, not left to work it out from an absence.
    val napping = openSleep
    when {
        napping != null -> NextUpCard(
            label = "NAPPING NOW",
            badge = Fmt.elapsed(napping.startAt),
            title = "Asleep since ${Fmt.time(napping.startAt)}",
            subtitle = napping.place?.takeIf { it.isNotBlank() } ?: "Tap to wake and record it",
            action = "End the nap",
            photoUri = photoUri,
            name = name,
            onAction = { onQuickLog(LogKind.SLEEP) },
            onEditPhoto = onEditPhoto,
            onCard = { onQuickLog(LogKind.SLEEP) },
        )

        nextVac != null -> nextVac?.let { g ->
            NextUpCard(
                label = "NEXT UP",
                badge = Fmt.dueText(g.inDays),
                title = "${g.label} vaccines · ${g.count} ${Fmt.plural(g.count.toLong(), "dose")}",
                subtitle = "${Fmt.date(g.dueDate)} · ${g.names}",
                action = "See schedule",
                photoUri = photoUri,
                name = name,
                onAction = { go.push(Routes.VACCINES) },
                onEditPhoto = onEditPhoto,
                onCard = { go.push(Routes.VACCINES) },
            )
        }

        // Green, not the brand's coral. Coral is what the app uses for anything asking
        // something of a parent, and this card's whole point is that nothing is.
        else -> NextUpCard(
            label = "NOTHING PLANNED",
            badge = "All clear",
            title = "Nothing due today",
            subtitle = "No doses, no visits, nothing overdue.",
            action = "See schedule",
            colors = listOf(KC.LeafDeep, KC.Leaf),
            photoUri = photoUri,
            name = name,
            onAction = { go.push(Routes.VACCINES) },
            onEditPhoto = onEditPhoto,
            onCard = { go.push(Routes.VACCINES) },
        )
    }

    // One card, two jobs: what it says is when this last happened, what it does is log the
    // next one. Reaching the Log tab to record a feed was four taps from here.
    SectionLabel("Quick actions", Modifier.padding(top = 2.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(LogKind.FEED, LogKind.SLEEP, LogKind.DIAPER).forEach { kind ->
            val spec = tileSpec(kind)
            val agoText = when {
                kind == LogKind.SLEEP && openSleep != null ->
                    "asleep ${Fmt.elapsed(openSleep!!.startAt)}"
                else -> Fmt.ago(latest[kind]?.startAt)
            }
            KCard(
                Modifier.weight(1f), corner = 22,
                background = spec.wash, border = null,
                onClick = { onQuickLog(kind) },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(KIcons[spec.icon], null, tint = spec.fg, modifier = Modifier.size(23.dp))
                        }
                        // Says the card does something, without a button competing with it.
                        Box(
                            Modifier
                                .size(19.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(1.5.dp)
                                .clip(CircleShape)
                                .background(spec.fg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                KIcons["add"], null,
                                tint = Color.White, modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Text(
                        spec.title, fontFamily = Sans, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = KC.Ink,
                    )
                    Text(
                        agoText, fontFamily = Sans, fontSize = 11.sp, color = KC.MutedStrong,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    SectionLabel("Today", Modifier.padding(top = 2.dp))
    DueTaskList(vm, go, "Nothing due right now. 🎈")
}

/**
 * The headline card: whatever most wants attention, with the child's picture beside it.
 *
 * The label row spans the card so the badge sits at its far edge; below it the words take
 * seven tenths and truncate rather than shoving the photo around, and the photo takes three.
 */
@Composable
private fun NextUpCard(
    label: String,
    badge: String,
    title: String,
    subtitle: String,
    action: String,
    photoUri: String?,
    name: String,
    colors: List<Color> = listOf(KC.Coral, KC.Clay),
    onAction: () -> Unit,
    onEditPhoto: () -> Unit,
    onCard: () -> Unit,
) {
    GradientCard(colors, onClick = onCard) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, letterSpacing = 0.5.sp,
                color = Color.White.copy(alpha = 0.85f),
            )
            Pill(badge)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(0.7f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    fontFamily = Sans, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                WhiteButton(action, tint = colors.first(), onClick = onAction)
            }

            // Its own tap target inside a card that does something else: the picture is the
            // way to change the picture.
            ChildAvatar(
                photoUri = photoUri,
                name = name,
                modifier = Modifier.weight(0.3f).aspectRatio(1f),
                ring = Color.White.copy(alpha = 0.55f),
                onClick = onEditPhoto,
            )
        }
    }
}

// ── Variant B · Hero ────────────────────────────────────────────────────────

@Composable
private fun TodayHero(
    vm: KilkariViewModel,
    go: NavActions,
    name: String,
    dob: LocalDate,
    onEditDetails: () -> Unit,
) {
    val nextVac by vm.nextVaccine.collectAsStateWithLifecycle()
    val growth by vm.growth.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    val metric by vm.metricUnits.collectAsStateWithLifecycle()

    val ageDays = Fmt.daysSince(dob)
    val latestWeight = growth.lastOrNull { it.weightKg != null }?.weightKg
    val birthdayDays = Fmt.daysUntil(Fmt.nextBirthday(dob))

    Box(
        Modifier
            .fillMaxWidth()
            .bleedHorizontal(16.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(KC.CoralDeep, KC.Clay, KC.Gold)))
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onEditDetails),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.take(1).uppercase(), fontFamily = Display,
                        fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color.White,
                    )
                }
                Icon(
                    KIcons["notifications"], null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable { go.push(Routes.REMINDERS) }
                        .padding(8.dp),
                )
            }
            Column {
                Text(
                    name, fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp, color = Color.White, letterSpacing = (-0.6).sp,
                )
                Text(
                    "${Fmt.age(dob)} old · $ageDays ${Fmt.plural(ageDays.toLong(), "day")} of you",
                    fontFamily = Sans, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("Weight", Fmt.weight(latestWeight, metric), Modifier.weight(1f))
                HeroStat(
                    "Next vaccine",
                    nextVac?.let { "${it.inDays.coerceAtLeast(0)} days" } ?: "—",
                    Modifier.weight(1f),
                )
                HeroStat("Next birthday", "$birthdayDays d", Modifier.weight(1f))
            }
        }
    }

    SectionLabel("Today")
    DueTaskList(vm, go, "Nothing due right now. 🎈")

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel("Latest moment")
        Text(
            "Timeline",
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Coral,
            modifier = Modifier.clickable { go.push(Routes.TIMELINE) },
        )
    }
    val moment = timeline.firstOrNull()
    if (moment == null) {
        KCard(corner = 16) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "No moments yet", fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink,
                )
                Text(
                    "Add one from the timeline.",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
        }
    } else {
        KCard(corner = 16, onClick = { go.push(Routes.TIMELINE) }) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    moment.title, fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink,
                )
                Text(
                    "${Fmt.date(moment.date)} · ${moment.subtitle}",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeroStat(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(caption, fontFamily = Sans, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
        Text(
            value, fontFamily = Sans, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, color = Color.White, maxLines = 1,
        )
    }
}

// ── Variant C · Checklist ───────────────────────────────────────────────────

@Composable
private fun TodayChecklist(vm: KilkariViewModel, go: NavActions, dob: LocalDate) {
    val tasks by vm.dueTasks.collectAsStateWithLifecycle()
    val nextVac by vm.nextVaccine.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val paperwork by vm.paperwork.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val doneCount = tasks.count { it.done }
    val pct = if (tasks.isEmpty()) 0 else doneCount * 100 / tasks.size
    val dayCount = Fmt.daysSince(dob, today)

    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                "${Fmt.dayAndDate(today)} · day $dayCount",
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )
            Text("$doneCount of ${tasks.size} done", style = ScreenTitle, color = KC.Ink)
        }
        ProgressRing(pct)
    }

    DueTaskList(vm, go, "Nothing due right now. 🎈")

    SectionLabel("Coming up")

    nextVac?.takeIf { it.inDays > 0 }?.let { g ->
        UpcomingRow(
            icon = "vaccines", tint = KC.GoldDeep, background = KC.GoldBg,
            border = KC.GoldRing,
            title = "${g.label} vaccines",
            subtitle = "${Fmt.date(g.dueDate)} · ${g.count} ${Fmt.plural(g.count.toLong(), "dose")}",
            trailing = Fmt.dueBadge(g.inDays),
        ) { go.push(Routes.VACCINES) }
    }

    paperwork.firstOrNull { it.status == PaperworkStatus.ACTIVE }
        ?.takeIf { (it.inDays ?: 0) > DueTaskBuilder.PAPERWORK_LEAD_DAYS }
        ?.let { step ->
            UpcomingRow(
                icon = step.kind.icon, tint = KC.ClayDeep, background = KC.Surface, border = KC.Border,
                title = step.kind.title,
                subtitle = listOfNotNull(step.dueDate?.let { Fmt.dayAndDate(it) }, step.kind.leadText).joinToString(" · "),
                trailing = Fmt.dueBadge(step.inDays ?: 0),
            ) { go.push(Routes.PAPERWORK) }
        }

    appointments.firstOrNull { it.startAt.toLocalDate().isAfter(today) }?.let { appt ->
        UpcomingRow(
            icon = "stethoscope", tint = KC.Coral, background = KC.Surface, border = KC.Border,
            title = appt.title,
            subtitle = listOfNotNull(
                Fmt.dayAndDate(appt.startAt.toLocalDate()),
                Fmt.time(appt.startAt),
                appt.place,
            ).joinToString(", "),
            trailing = Fmt.dueBadge(Fmt.daysUntil(appt.startAt.toLocalDate())),
        ) { go.push(Routes.APPOINTMENTS) }
    }

    events.firstOrNull { it.date.isAfter(today) }?.let { ev ->
        UpcomingRow(
            icon = ev.icon, tint = KC.Gold, background = KC.Surface, border = KC.Border,
            title = ev.title,
            subtitle = Fmt.dayAndDate(ev.date),
            trailing = Fmt.dueBadge(Fmt.daysUntil(ev.date)),
        ) { go.push(Routes.EVENTS) }
    }
}

@Composable
private fun ProgressRing(percent: Int) {
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = KC.BorderStrong, startAngle = -90f, sweepAngle = 360f,
                useCenter = true, size = Size(size.width, size.height),
            )
            drawArc(
                color = KC.Clay, startAngle = -90f, sweepAngle = 360f * percent / 100f,
                useCenter = true, size = Size(size.width, size.height),
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(KC.Screen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$percent%", fontFamily = Sans, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = KC.Coral,
            )
        }
    }
}

@Composable
private fun UpcomingRow(
    icon: String,
    tint: Color,
    background: Color,
    border: Color,
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
) {
    KCard(corner = 14, background = background, border = border, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KIcons[icon], null, tint = tint, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = KC.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(trailing, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tint)
        }
    }
}

// ── Shared ──────────────────────────────────────────────────────────────────

/**
 * The one list of outstanding work, shared by all three layouts so they cannot disagree.
 * Tapping a tickable task completes it; anything else opens the screen that owns it.
 */
@Composable
private fun DueTaskList(vm: KilkariViewModel, go: NavActions, emptyText: String) {
    val tasks by vm.dueTasks.collectAsStateWithLifecycle()

    // Finished items are kept, not hidden — but folded away, because a day's worth of struck
    // through rows pushes what is still outstanding off the screen.
    var showDone by remember { mutableStateOf(false) }
    val (done, todo) = tasks.partition { it.done }

    if (tasks.isEmpty()) {
        KCard(corner = 14) {
            Text(
                emptyText,
                modifier = Modifier.padding(14.dp),
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )
        }
        return
    }

    if (todo.isEmpty()) {
        KCard(corner = 14) {
            Text(
                "All done for today. 🎈",
                modifier = Modifier.padding(14.dp),
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )
        }
    }
    todo.forEach { task -> DueTaskRow(task, vm, go) }

    if (done.isNotEmpty()) {
        DoneHeader(done.size, showDone) { showDone = !showDone }
        if (showDone) done.forEach { task -> DueTaskRow(task, vm, go) }
    }
}

/** The fold over the day's finished items: a count, and a way back to them. */
@Composable
private fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            KIcons[if (expanded) "expand_more" else "chevron_right"], null,
            tint = KC.Muted, modifier = Modifier.size(18.dp),
        )
        Text(
            "$count done",
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = KC.Muted,
        )
    }
}

@Composable
private fun DueTaskRow(task: DueTask, vm: KilkariViewModel, go: NavActions) {
    val skin = taskSkin(task)
    // Half-strength: the tile washes are mixed for a card the size of a thumb, and six rows
    // of one at full strength is a paint chart. Mixed rather than faded, so the card stays
    // opaque — see [onCream]. Done rows drop back to white: the colour is there to say what a
    // thing is while it still wants doing.
    KCard(
        corner = 16,
        background = if (task.done) KC.Surface else skin.second.onCream(0.5f),
        border = null,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    when {
                        // Anything that needs data takes you to where that data is entered,
                        // and completes itself once the entry lands.
                        task.requiresEntry && task.route != null -> {
                            vm.requestEntry(task.id.removePrefix("rem:"))
                            go.open(task.route)
                        }
                        task.completable -> vm.setTaskDone(task, !task.done)
                        task.route != null -> go.open(task.route)
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (task.completable) {
                // Only reminders carry an icon the parent picked; everything else keeps the
                // plain ring rather than repeating an icon the row's text already says.
                CheckRing(task.done, glyph = task.icon.takeIf { task.kind == DueTaskKind.REMINDER })
            } else {
                IconBadge(task.icon, skin.first, KC.Surface)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = if (task.done) KC.Faint else KC.Ink,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (task.subtitle.isNotBlank()) {
                    Text(
                        task.subtitle,
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.dismissible && !task.done) {
                Text(
                    "Dismiss",
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { vm.dismissTask(task) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Faint,
                )
            }
            Text(
                task.trailing,
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                // Only genuine lateness is red; the per-kind tint made every medicine look overdue.
                color = when {
                    task.done -> KC.Faint
                    task.overdue -> KC.Danger
                    else -> KC.Muted
                },
            )
        }
    }
}

/**
 * The colour a due row wears, matched to the screen its subject lives on: a vaccine is teal
 * because vaccines are on Health, a nappy reminder is gold because that is the Log tile, and
 * so on. The row's ground is the pale step and its icon the deep one, so a list of six kinds
 * reads as six things rather than six identical white bars.
 */
private fun taskSkin(task: DueTask): Pair<Color, Color> = when (task.kind) {
    DueTaskKind.MEDICATION -> KC.ClayDeep to KC.ClayWash
    DueTaskKind.APPOINTMENT -> KC.CoralDeep to KC.CoralWash
    DueTaskKind.VACCINE -> KC.TealDeep to KC.TealWash
    DueTaskKind.PAPERWORK -> KC.SeaDeep to KC.SeaWash
    DueTaskKind.SLEEP -> KC.LilacDeep to KC.LilacWash
    DueTaskKind.REMINDER -> KC.GoldDeep to KC.GoldWash
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontFamily = Sans, fontSize = 12.sp, color = Color.White)
    }
}

@Composable
private fun WhiteButton(
    label: String,
    modifier: Modifier = Modifier,
    /** Taken from the card it sits on, so a coral label never turns up on a green card. */
    tint: Color = KC.Coral,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tint)
    }
}

/**
 * How one kind of log entry is drawn wherever it appears: the tile on Log, the quick action
 * on Today, the row in the day's list.
 *
 * [bg] is the pale ground behind a small icon; [wash] is the ground for a whole tile. The six
 * kinds deliberately take six different hues rather than six shades of the brand — at tile
 * size the colour is how a parent finds "sleep" without reading, and a grid of one colour was
 * the single biggest reason the app looked like a form.
 */
internal data class TileSpec(
    val icon: String,
    val title: String,
    val fg: Color,
    val bg: Color,
    val wash: Color,
)

internal fun tileSpec(kind: LogKind): TileSpec = when (kind) {
    LogKind.FEED -> TileSpec("water_drop", "Feed", KC.GoldDeep, KC.GoldBg, KC.GoldWash)
    LogKind.SLEEP -> TileSpec("bedtime", "Sleep", KC.LilacDeep, KC.LilacBg, KC.LilacWash)
    LogKind.DIAPER -> TileSpec("baby_changing_station", "Diaper", KC.SeaDeep, KC.SeaBg, KC.SeaWash)
    LogKind.MEDICINE -> TileSpec("pill", "Medicine", KC.CoralDeep, KC.CoralBg, KC.CoralWash)
    LogKind.GROWTH -> TileSpec("monitor_weight", "Growth", KC.LeafDeep, KC.LeafBg, KC.LeafWash)
    LogKind.TOOTH -> TileSpec("dentistry", "Teeth", KC.ClayDeep, KC.ClayBg, KC.ClayWash)
}
