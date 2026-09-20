package com.kilkari.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.R
import com.kilkari.data.seed.MilestoneDef
import com.kilkari.data.seed.Milestones
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Currency
import com.kilkari.domain.Fmt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.Spot
import com.kilkari.ui.components.Illustration
import com.kilkari.ui.components.CheckRing
import com.kilkari.ui.components.Hint
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.BlobPortrait
import com.kilkari.ui.components.BlobBackdrop
import com.kilkari.ui.theme.KAccents
import com.kilkari.ui.theme.AccentScope
import com.kilkari.ui.theme.Accent
import com.kilkari.ui.theme.KGradients
import androidx.compose.ui.graphics.SolidColor
import com.kilkari.ui.components.KIcons
import androidx.compose.ui.layout.ContentScale
import com.kilkari.ui.components.KCard
import com.kilkari.data.seed.VaccineGroupDef
import com.kilkari.ui.components.milestoneAge
import com.kilkari.ui.components.VaccineCatchUpList
import com.kilkari.ui.components.MilestoneCatchUpList
import com.kilkari.ui.components.MeasurementRows
import com.kilkari.ui.components.MEASUREMENT_KEYBOARD
import com.kilkari.ui.components.MeasurementState
import com.kilkari.domain.Sex
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.RadioRow
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.components.ValueField
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Everything the wizard collects, held in one place so steps stay declarative. */
private class OnboardingState(metric: Boolean) {
    var name by mutableStateOf("")
    var dob by mutableStateOf<LocalDate?>(null)
    var sex by mutableStateOf<Sex?>(null)
    var place by mutableStateOf("")

    /** Typed in whichever units the phone is set to; read back in kg and cm. */
    val measurements = MeasurementState(null, null, null, metric)
    var scheduleId by mutableStateOf("iap")
    var currency by mutableStateOf(Currency.INR)

    /** Vaccine group label → the date the parent says it was given. */
    val givenGroups = mutableStateMapOf<String, LocalDate>()

    /** Milestone key → the date it happened. */
    val reachedMilestones = mutableStateMapOf<String, LocalDate>()
}

/**
 * First run, as a short sequence rather than one long form.
 *
 * The last two steps exist because almost nobody starts tracking on the day of birth: once the
 * date of birth and schedule are known, the app can work out which vaccines and milestones are
 * already behind them, and offer to record those instead of leaving gaps.
 */
@Composable
fun OnboardingScreen(vm: KilkariViewModel) {
    val metric by vm.metricUnits.collectAsStateWithLifecycle()
    val state = remember(metric) { OnboardingState(metric) }
    var step by remember { mutableIntStateOf(0) }

    val dob = state.dob
    val ageMonths = dob?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) / 30.44 } ?: 0.0
    val overdueGroups = remember(dob, state.scheduleId) {
        val d = dob ?: return@remember emptyList()
        VaccineSchedules.byId(state.scheduleId).groups
            .map { it to d.plusDays(it.days.toLong()) }
            .filter { !it.second.isAfter(LocalDate.now()) }
    }
    val passedMilestones = remember(ageMonths) { Milestones.passedBy(ageMonths) }

    // Steps that would have nothing to show are dropped rather than shown empty.
    val steps = remember(overdueGroups, passedMilestones) {
        buildList {
            add(Step.WELCOME); add(Step.BABY); add(Step.MEASUREMENTS)
            add(Step.SCHEDULE); add(Step.CURRENCY)
            if (overdueGroups.isNotEmpty()) add(Step.CATCH_UP_VACCINES)
            if (passedMilestones.isNotEmpty()) add(Step.CATCH_UP_MILESTONES)
            add(Step.DONE)
        }
    }
    val current = steps[step.coerceIn(0, steps.lastIndex)]

    // The welcome sits on the warm gradient the splash hands over; the form steps after it go
    // back to the flat cream, where fields are easier to read.
    AccentScope(current.accent) {
    BlobBackdrop(
        Modifier.fillMaxSize(),
        brush = if (current == Step.WELCOME) KGradients.welcome else SolidColor(KC.Screen),
        animated = current == Step.WELCOME,
    ) {
    Column(Modifier.fillMaxSize()) {
        if (current != Step.WELCOME) {
            StepHeader(
                index = step,
                total = steps.size,
                onBack = { if (step > 0) step-- },
            )
        }

        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut()) using SizeTransform(clip = false)
            },
            modifier = Modifier.weight(1f),
            label = "onboarding-step",
        ) { target ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                target.art?.let { art ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Illustration(art, 148.dp, Modifier.padding(top = 4.dp))
                    }
                }
                when (target) {
                    Step.WELCOME -> WelcomeStep()
                    Step.BABY -> BabyStep(state)
                    Step.MEASUREMENTS -> MeasurementsStep(state)
                    Step.SCHEDULE -> ScheduleStep(state)
                    Step.CURRENCY -> CurrencyStep(state)
                    Step.CATCH_UP_VACCINES -> VaccineCatchUpStep(state, overdueGroups)
                    Step.CATCH_UP_MILESTONES -> MilestoneCatchUpStep(state, passedMilestones, dob)
                    Step.DONE -> DoneStep(state, overdueGroups.size, passedMilestones.size)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val canAdvance = when (current) {
                Step.BABY -> state.name.isNotBlank() && dob != null && !dob.isAfter(LocalDate.now())
                else -> true
            }
            PrimaryButton(
                label = when (current) {
                    Step.WELCOME -> "Begin your journey"
                    Step.DONE -> "Start tracking"
                    else -> "Continue"
                },
                enabled = canAdvance,
            ) {
                if (current == Step.DONE) {
                    vm.onboard(
                        name = state.name.trim(),
                        dob = dob!!,
                        birthTime = null,
                        weightKg = state.measurements.weightKg,
                        lengthCm = state.measurements.lengthCm,
                        headCm = state.measurements.headCm,
                        sex = state.sex,
                        place = state.place.trim().ifBlank { null },
                        scheduleId = state.scheduleId,
                        currency = state.currency,
                        givenGroups = state.givenGroups.toMap(),
                        milestones = state.reachedMilestones.toMap(),
                    )
                } else {
                    step++
                }
            }
            if (current == Step.MEASUREMENTS || current == Step.CATCH_UP_VACCINES ||
                current == Step.CATCH_UP_MILESTONES
            ) {
                Text(
                    "Skip for now",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { step++ }
                        .padding(vertical = 8.dp),
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = KC.Muted, textAlign = TextAlign.Center,
                )
            }
        }
    }
    }
    }
}

/**
 * The steps, each with the colour of what it asks for: the baby in the brand's coral, the
 * measurements in green, the schedule in the health teal, the currency in gold. Setting up
 * the app is the first thing a parent sees of it, so it is also where the palette introduces
 * itself.
 */
private enum class Step(val accent: Accent, val art: Spot?) {
    WELCOME(KAccents.Quiet, null),
    BABY(KAccents.Brand, Spot.ONBOARD_BABY),
    MEASUREMENTS(KAccents.Growth, Spot.ONBOARD_MEASUREMENTS),
    SCHEDULE(KAccents.Health, Spot.ONBOARD_SCHEDULE),
    CURRENCY(KAccents.Money, Spot.ONBOARD_MONEY),
    CATCH_UP_VACCINES(KAccents.Care, null),
    CATCH_UP_MILESTONES(KAccents.Memories, null),
    DONE(KAccents.Brand, Spot.ONBOARD_DONE),
}

@Composable
private fun StepHeader(index: Int, total: Int, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Back",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = KC.Muted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Step $index of ${total - 1}",
                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(total - 1) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < index) KC.Coral else KC.BorderStrong),
                )
            }
        }
    }
}

// ── Steps ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.WelcomeStep() {
    // The brand as a stamp, not the subject: the picture below is what the screen is about.
    Row(
        Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        Text(
            "Kilkari",
            fontFamily = Display, fontWeight = FontWeight.ExtraBold,
            fontSize = 19.sp, color = KC.MutedStrong, letterSpacing = (-0.3).sp,
        )
    }

    Spacer(Modifier.height(18.dp))
    Text(
        "Your journey to\nconfident parenting",
        modifier = Modifier.align(Alignment.CenterHorizontally),
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp, lineHeight = 38.sp, color = KC.Ink, letterSpacing = (-0.9).sp,
        textAlign = TextAlign.Center,
    )
    Text(
        "Feeds, sleep, vaccines, growth and money — in one place, on this phone, for whoever " +
            "is holding the baby.",
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 10.dp, start = 8.dp, end = 8.dp),
        fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp, color = KC.MutedStrong,
        textAlign = TextAlign.Center,
    )

    // Three promises rather than a feature list: what a parent is actually agreeing to.
    Spacer(Modifier.height(16.dp))
    FlowRow(
        Modifier.align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WelcomePill("cloud_off", "Works offline")
        WelcomePill("lock", "No account")
        WelcomePill("bolt", "One tap to log")
    }

    Text(
        "Already a few weeks in? Kilkari asks about the vaccines and moments already behind " +
            "you, so nothing is missing.",
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 16.dp, start = 8.dp, end = 8.dp),
        fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
        textAlign = TextAlign.Center,
    )

    // The picture closes the screen, running to the bottom edge behind the button.
    Image(
        painter = painterResource(R.drawable.welcome_family),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 10.dp)
            .fillMaxWidth(0.92f),
        contentScale = ContentScale.FillWidth,
    )
}

/** A small reassurance on the welcome screen: an icon and two or three words. */
@Composable
private fun WelcomePill(icon: String, label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KIcons[icon], null, tint = KC.LilacDeep, modifier = Modifier.size(15.dp))
        Text(
            label, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, color = KC.MutedStrong,
        )
    }
}

@Composable
private fun ColumnScope.BabyStep(state: OnboardingState) {
    StepTitle("Who are we tracking?")
    Hint("Just a name and a birthday to begin with.")
    KCard {
        ValueField("Name", state.name, "e.g. Avika") { state.name = it }
        KDateField(
            "Date of birth", state.dob, "Pick a date",
            sheetStyle = false, selectableTo = LocalDate.now(),
        ) { state.dob = it }
        ValueField("Born at", state.place, "Hospital or city", divider = false) { state.place = it }
    }
    state.dob?.let { Hint("${state.name.ifBlank { "Baby" }} is ${Fmt.age(it)} today.") }

    // Optional, and only the growth chart uses it — the WHO curve is published per sex, so
    // without this the chart can only show the average of the two.
    Hint("Sex — sets the growth curve the weight chart compares against. You can skip it.")
    KSegmented(Sex.entries.map { it.label }, Sex.entries.indexOf(state.sex)) {
        state.sex = Sex.entries[it]
    }
}

@Composable
private fun ColumnScope.MeasurementsStep(state: OnboardingState) {
    StepTitle("Birth measurements")
    Hint("Optional — it gives the growth chart a starting point.")
    KCard {
        MeasurementRows(state.measurements) { label, value, hint, last, onChange ->
            ValueField(label, value, hint, divider = !last, keyboard = MEASUREMENT_KEYBOARD, onChange = onChange)
        }
    }
}

@Composable
private fun ColumnScope.ScheduleStep(state: OnboardingState) {
    StepTitle("Vaccination schedule")
    Hint("Due dates are generated from the date of birth. You can change this later.")
    KCard {
        VaccineSchedules.all.forEachIndexed { i, s ->
            RadioRow(
                title = s.name,
                subtitle = s.desc,
                selected = state.scheduleId == s.id,
                divider = i != VaccineSchedules.all.lastIndex,
            ) { state.scheduleId = s.id }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CurrencyStep(state: OnboardingState) {
    StepTitle("Currency")
    Hint("Used everywhere money appears — expenses, the savings fund, investments.")
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Currency.entries.forEach { c ->
            KChip("${c.symbol} ${c.code}", state.currency == c) { state.currency = c }
        }
    }
}

@Composable
private fun ColumnScope.VaccineCatchUpStep(
    state: OnboardingState,
    overdue: List<Pair<VaccineGroupDef, LocalDate>>,
) {
    StepTitle("Already had these?")
    Hint(
        "These doses were due before today. Tick the ones already given and Kilkari will " +
            "record them — the rest stay on the schedule as due."
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KChip("Mark all given", false) {
            overdue.forEach { (g, due) -> state.givenGroups[g.label] = due }
        }
        KChip("Clear", false) { state.givenGroups.clear() }
    }
    VaccineCatchUpList(overdue, state.givenGroups)
}

@Composable
private fun ColumnScope.MilestoneCatchUpStep(
    state: OnboardingState,
    passed: List<MilestoneDef>,
    dob: LocalDate?,
) {
    StepTitle("Anything already happened?")
    Hint("Tick what you remember. Each one becomes an entry on the timeline.")
    MilestoneCatchUpList(passed, dob, state.reachedMilestones)
}
@Composable
private fun ColumnScope.DoneStep(state: OnboardingState, overdue: Int, passed: Int) {
    val dob = state.dob
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(KC.CoralDeep, KC.Clay, KC.Gold)))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "All set",
                fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp, color = Color.White, letterSpacing = (-0.64).sp,
            )
            Text(
                dob?.let { "${state.name.trim()} is ${Fmt.age(it)} old." }.orEmpty(),
                fontFamily = Sans, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
    SectionLabel("What Kilkari will set up")
    KCard {
        val schedule = VaccineSchedules.byId(state.scheduleId)
        SummaryRow("vaccines", "${schedule.shortName} schedule", "Generated from the date of birth")
        val recorded = state.givenGroups.size
        SummaryRow(
            "check_circle",
            "$recorded of $overdue past ${Fmt.plural(overdue.toLong(), "group")} recorded",
            when {
                overdue == 0 -> "Nothing was overdue"
                recorded == overdue -> "Nothing left outstanding"
                else -> "The rest stay marked as due"
            },
        )
        val moments = state.reachedMilestones.size
        SummaryRow(
            "auto_awesome",
            "$moments ${Fmt.plural(moments.toLong(), "milestone")} on the timeline",
            if (passed == 0) "None expected at this age yet" else "You can add more any time",
        )
        SummaryRow(
            "payments",
            "${state.currency.symbol} ${state.currency.code}",
            "Used for expenses, the fund and investments",
            divider = false,
        )
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun StepTitle(text: String) {
    Text(text, style = ScreenTitle, color = KC.Ink)
}

@Composable
private fun Warning(text: String) {
    Text(text, fontFamily = Sans, fontSize = 13.sp, color = KC.Danger)
}

@Composable
private fun SummaryRow(icon: String, title: String, subtitle: String, divider: Boolean = true) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, KC.Coral, KC.CoralBg, size = 34, corner = 10, iconSize = 18)
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = KC.Ink,
                )
                Text(subtitle, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
    }
}

/** Accepts DD-MM-YYYY with `-`, `/` or `.` separators. */
internal fun parseDate(text: String): LocalDate? {
    val parts = text.split('-', '/', '.').map { it.trim() }
    if (parts.size != 3) return null
    val (d, m, y) = parts
    return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
}
