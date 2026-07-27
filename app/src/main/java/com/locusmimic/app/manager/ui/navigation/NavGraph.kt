package com.locusmimic.app.manager.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.locusmimic.app.data.repository.PreferencesRepository
import com.locusmimic.app.manager.ui.disclaimer.DisclaimerScreen
import com.locusmimic.app.manager.ui.favorites.FavoritesScreen
import com.locusmimic.app.manager.ui.map.MapScreen
import com.locusmimic.app.manager.ui.map.MapViewModel
import com.locusmimic.app.manager.ui.permissions.PermissionsScreen
import com.locusmimic.app.manager.ui.settings.SettingsScreen
import com.locusmimic.app.manager.ui.targetapps.TargetAppsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
) {
    val mapViewModel: MapViewModel = viewModel()
    val context = LocalContext.current
    val preferencesRepository = remember {
        PreferencesRepository(context.applicationContext)
    }
    val hasAcceptedDisclaimer = remember {
        preferencesRepository.hasAcceptedDisclaimer()
    }
    val firstContentRoute = Screen.Permissions.route
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val isPrimaryTab = currentRoute == Screen.Map.route

    BackHandler(enabled = isPrimaryTab) {
        // A bottom tab is a top-level destination. Android back should leave the app instead of
        // replaying a history of sibling tabs.
        (context as? Activity)?.finish()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            modifier = Modifier.fillMaxSize(),
            navController = navController,
            startDestination = if (hasAcceptedDisclaimer) firstContentRoute else Screen.Disclaimer.route,
            enterTransition = {
            val from = initialState.destination.route.primaryTabIndex()
            val to = targetState.destination.route.primaryTabIndex()
            if (from >= 0 && to >= 0) {
                slideInHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    initialOffsetX = { width -> if (to > from) width else -width }
                )
            } else {
                fadeIn(animationSpec = tween(180))
            }
        },
        exitTransition = {
            val from = initialState.destination.route.primaryTabIndex()
            val to = targetState.destination.route.primaryTabIndex()
            if (from >= 0 && to >= 0) {
                slideOutHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    targetOffsetX = { width -> if (to > from) -width else width }
                )
            } else {
                fadeOut(animationSpec = tween(90))
            }
        },
        popEnterTransition = {
            val from = initialState.destination.route.primaryTabIndex()
            val to = targetState.destination.route.primaryTabIndex()
            if (from >= 0 && to >= 0) {
                slideInHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    initialOffsetX = { width -> if (to > from) width else -width }
                )
            } else {
                fadeIn(animationSpec = tween(180))
            }
        },
        popExitTransition = {
            val from = initialState.destination.route.primaryTabIndex()
            val to = targetState.destination.route.primaryTabIndex()
            if (from >= 0 && to >= 0) {
                slideOutHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    targetOffsetX = { width -> if (to > from) -width else width }
                )
            } else {
                fadeOut(animationSpec = tween(90))
            }
        },
        ) {
        composable(route = Screen.Disclaimer.route) {
            DisclaimerScreen(
                navController = navController,
                preferencesRepository = preferencesRepository,
                nextRoute = firstContentRoute
            )
        }
        composable(route = Screen.Favorites.route) {
            FavoritesScreen(navController = navController)
        }
        composable(route = Screen.Map.route) {
            MapScreen(navController = navController, mapViewModel = mapViewModel)
        }
        composable(route = Screen.Permissions.route) {
            PermissionsScreen(navController = navController)
        }
            composable(route = Screen.Settings.route) {
                LocusMimicTabShell {
                SettingsScreen(navController = navController)
                }
            }
            composable(route = Screen.TargetApps.route) {
                LocusMimicTabShell {
                TargetAppsScreen(navController = navController)
                }
            }
        }

    }
}

/** Matches the visible order in the floating bottom bar: Apps ← Home → Settings. */
private fun String?.primaryTabIndex(): Int = when (this) {
    Screen.TargetApps.route -> 0
    Screen.Map.route -> 1
    Screen.Settings.route -> 2
    else -> -1
}
