package com.pixson.apbfit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pixson.apbfit.ui.screen.ActiveRunScreen
import com.pixson.apbfit.ui.screen.HistoryScreen
import com.pixson.apbfit.ui.screen.HomeScreen
import com.pixson.apbfit.ui.screen.SettingsScreen
import com.pixson.apbfit.ui.screen.SignInScreen
import com.pixson.apbfit.ui.viewmodel.RootViewModel

@Composable
fun ApbFitNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val activeAccount by rootViewModel.accountRepository.activeAccount.collectAsStateWithLifecycle()
    val runState by rootViewModel.runStateHolder.state.collectAsStateWithLifecycle()

    LaunchedEffect(activeAccount, runState.isActive) {
        val destination = when {
            activeAccount == null -> Routes.SIGN_IN
            runState.isActive -> Routes.ACTIVE_RUN
            else -> Routes.HOME
        }
        android.util.Log.d(
            "APBFit_Run",
            "Nav gate: destination=$destination isActive=${runState.isActive} runId=${runState.runId} status=${runState.status}",
        )
        if (navController.currentDestination?.route != destination) {
            navController.navigate(destination) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SIGN_IN,
        modifier = modifier,
    ) {
        composable(Routes.SIGN_IN) { SignInScreen() }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.ACTIVE_RUN) { ActiveRunScreen() }
        composable(Routes.HISTORY) {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
