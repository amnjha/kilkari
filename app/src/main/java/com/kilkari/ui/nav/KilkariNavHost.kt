package com.kilkari.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kilkari.data.repo.KilkariRepository
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.KBottomNav
import com.kilkari.ui.components.KToast
import com.kilkari.ui.screens.AppointmentsScreen
import com.kilkari.ui.screens.BackupScreen
import com.kilkari.ui.screens.DoctorsScreen
import com.kilkari.ui.screens.DocumentDetailScreen
import com.kilkari.ui.screens.DocumentsScreen
import com.kilkari.ui.screens.EventsScreen
import com.kilkari.ui.screens.GrowthScreen
import com.kilkari.ui.screens.HealthScreen
import com.kilkari.ui.screens.LogScreen
import com.kilkari.ui.screens.MedsScreen
import com.kilkari.ui.screens.MoneyScreen
import com.kilkari.ui.screens.MoreScreen
import com.kilkari.ui.screens.OnboardingScreen
import com.kilkari.ui.screens.PhotosScreen
import com.kilkari.ui.screens.RemindersScreen
import com.kilkari.ui.screens.SettingsScreen
import com.kilkari.ui.screens.SplashScreen
import com.kilkari.ui.screens.TeethScreen
import com.kilkari.ui.screens.TimelineScreen
import com.kilkari.ui.screens.TodayScreen
import com.kilkari.ui.screens.VaccineDetailScreen
import com.kilkari.ui.screens.VaccinesScreen

@Composable
fun KilkariNavHost(repository: KilkariRepository, onReady: () -> Unit = {}) {
    val vm: KilkariViewModel = viewModel(factory = KilkariViewModel.Factory(repository))
    val nav = rememberNavController()

    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Release the system splash the moment the database answers, then let the Compose splash
    // cover whatever is left of the first composition.
    LaunchedEffect(loaded) { if (loaded) onReady() }

    if (!loaded) {
        SplashScreen()
        return
    }

    // Onboarding is a one-way door: once a baby exists we replace it in the back stack.
    LaunchedEffect(settings.onboarded, baby) {
        val needsOnboarding = baby == null
        if (needsOnboarding && route != Routes.ONBOARDING) {
            nav.navigate(Routes.ONBOARDING) { popUpTo(0) }
        } else if (!needsOnboarding && route == Routes.ONBOARDING) {
            nav.navigate(Routes.TODAY) { popUpTo(0) }
        }
    }

    LaunchedEffect(baby?.id) { if (baby != null) vm.refreshToday() }

    val go = remember(nav) { NavActions(nav) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = if (baby == null) Routes.ONBOARDING else Routes.TODAY,
                modifier = Modifier.weight(1f),
            ) {
                composable(Routes.ONBOARDING) { OnboardingScreen(vm) }

                composable(Routes.TODAY) { TodayScreen(vm, go) }
                composable(Routes.LOG) { LogScreen(vm, go) }
                composable(Routes.HEALTH) { HealthScreen(vm, go) }
                composable(Routes.MONEY) { MoneyScreen(vm) }
                composable(Routes.MORE) { MoreScreen(vm, go) }

                composable(Routes.VACCINES) { VaccinesScreen(vm, go) }
                composable(Routes.VACCINE_DETAIL) { VaccineDetailScreen(vm, go) }
                composable(Routes.GROWTH) { GrowthScreen(vm, go) }
                composable(Routes.TEETH) { TeethScreen(vm, go) }
                composable(Routes.MEDS) { MedsScreen(vm, go) }
                composable(Routes.APPOINTMENTS) { AppointmentsScreen(vm, go) }
                composable(Routes.DOCTORS) { DoctorsScreen(vm, go) }

                composable(Routes.TIMELINE) { TimelineScreen(vm, go) }
                composable(Routes.DOCUMENTS) { DocumentsScreen(vm, go) }
                composable(Routes.DOCUMENT_DETAIL) { DocumentDetailScreen(vm, go) }
                composable(Routes.PHOTOS) { PhotosScreen(vm, go) }
                composable(Routes.EVENTS) { EventsScreen(vm, go) }
                composable(Routes.REMINDERS) { RemindersScreen(vm, go) }
                composable(Routes.BACKUP) { BackupScreen(vm, go) }
                composable(Routes.SETTINGS) { SettingsScreen(vm, go) }
            }

            if (Routes.showsNav(route)) {
                KBottomNav(
                    tabs = Routes.tabs,
                    active = Routes.activeTab(route),
                    onSelect = go::tab,
                )
            }
        }

        // Float the toast clear of the 76dp navigation bar when it is showing.
        KToast(toast, bottomInset = if (Routes.showsNav(route)) 92.dp else 16.dp)
    }
}

/** Thin wrapper so screens navigate by intent rather than by juggling NavOptions. */
class NavActions(private val nav: NavHostController) {

    /**
     * Switches to a tab's own screen, clearing anything pushed on top of it.
     *
     * The tabs share one flat graph, so the usual saveState/restoreState pair — which belongs
     * to per-tab nested graphs — saved the pushed stack under the tab's destination and handed
     * it straight back. Tapping Health from a vaccine detail screen returned you to that detail
     * screen, and the only way back to the hub was repeated Back presses. Tabs are entry points,
     * so popping to the start destination and landing on the tab root is the behaviour wanted.
     */
    fun tab(route: String) = nav.navigate(route) {
        popUpTo(Routes.TODAY) { inclusive = false }
        launchSingleTop = true
    }

    fun push(route: String) = nav.navigate(route) { launchSingleTop = true }

    /** Go to a destination without needing to know whether it owns a tab. */
    fun open(route: String) = if (Routes.tabs.any { it.route == route }) tab(route) else push(route)

    fun back() {
        if (!nav.popBackStack()) nav.navigate(Routes.TODAY) { popUpTo(0) }
    }
}
