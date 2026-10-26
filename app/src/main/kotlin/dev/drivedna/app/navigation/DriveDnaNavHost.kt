package dev.drivedna.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.drivedna.app.DriveDnaApplication
import dev.drivedna.app.ui.components.RestrictedContent
import dev.drivedna.app.ui.screens.HistoryScreen
import dev.drivedna.app.ui.screens.HomeScreen
import dev.drivedna.app.ui.screens.ModeStatusScreen
import dev.drivedna.app.ui.screens.OnboardingScreen
import dev.drivedna.app.ui.screens.MyDnaScreen
import dev.drivedna.app.ui.screens.SettingsScreen
import dev.drivedna.app.ui.screens.TripReplayScreen
import dev.drivedna.app.ui.screens.TripResultScreen
import dev.drivedna.core.domain.model.DrivingRestriction

@Composable
fun DriveDnaNavHost(app: DriveDnaApplication, restriction: DrivingRestriction, consentMissing: Boolean) {
    val nav = rememberNavController()
    // Chosen once: changing NavHost's start destination rebuilds the graph and drops the back stack.
    val start = remember { if (consentMissing) Destination.Onboarding.route else Destination.Home.route }
    // AAOS can restore a stale back stack (e.g. after data was cleared); without consent always show S01.
    LaunchedEffect(consentMissing) {
        if (consentMissing && nav.currentDestination?.route != Destination.Onboarding.route) {
            nav.navigate(Destination.Onboarding.route) { popUpTo(0) }
        }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val home: () -> Unit = { nav.navigate(Destination.Home.route) { popUpTo(0) } }

    // Detailed screens are locked while driving or while the state is unknown (FR-011, T09).
    @Composable
    fun Locked(content: @Composable () -> Unit) = RestrictedContent(restriction, content)

    NavHost(navController = nav, startDestination = start) {
        composable(Destination.Onboarding.route) {
            OnboardingScreen(
                preferences = app.preferences,
                onLiveChosen = { nav.navigate(Destination.ModeStatus.route) { popUpTo(0) } },
                onDemoChosen = home,
            )
        }
        composable(Destination.ModeStatus.route) {
            ModeStatusScreen(app, onDemo = { nav.navigateSingle(Destination.TripReplay.route) }, onBack = home)
        }
        composable(Destination.Home.route) {
            HomeScreen(app, restriction, onNavigate = { nav.navigateSingle(it) })
        }
        composable(Destination.TripReplay.route) {
            Locked {
                TripReplayScreen(
                    recorder = app.tripRecorder,
                    onResult = { id -> nav.navigate(Destination.tripResult(id)) { popUpTo(Destination.TripReplay.route) { inclusive = true } } },
                    onBack = back,
                )
            }
        }
        composable(
            Destination.TripResult.route,
            arguments = listOf(navArgument(Destination.TRIP_ID_ARG) { type = NavType.LongType }),
        ) { entry ->
            Locked { TripResultScreen(app, entry.arguments!!.getLong(Destination.TRIP_ID_ARG), onBack = back) }
        }
        composable(Destination.MyDna.route) {
            Locked { MyDnaScreen(app, onBack = back) }
        }
        composable(Destination.History.route) {
            Locked { HistoryScreen(app, onOpen = { nav.navigateSingle(Destination.tripResult(it)) }, onBack = back) }
        }
        composable(Destination.Settings.route) {
            Locked {
                SettingsScreen(app, onDeleted = { nav.navigate(Destination.Onboarding.route) { popUpTo(0) } }, onBack = back)
            }
        }
    }
}

private fun NavHostController.navigateSingle(route: String) = navigate(route) { launchSingleTop = true }
