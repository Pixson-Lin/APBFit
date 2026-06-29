package com.pixsonlin.apbfit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pixsonlin.apbfit.ui.screen.ActiveRunScreen
import com.pixsonlin.apbfit.ui.screen.HistoryScreen
import com.pixsonlin.apbfit.ui.screen.HomeScreen
import com.pixsonlin.apbfit.ui.screen.SettingsScreen
import com.pixsonlin.apbfit.ui.screen.SignInScreen
import com.pixsonlin.apbfit.ui.viewmodel.RootViewModel

@Composable
fun ApbFitNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val accountRevision by rootViewModel.accountRepository.accountRevision.collectAsStateWithLifecycle()
    val sessionState by rootViewModel.runSessionStateHolder.state.collectAsStateWithLifecycle()
    val hasAccounts = remember(accountRevision) {
        rootViewModel.accountRepository.getKnownAccounts().isNotEmpty()
    }
    var wasSessionActive by remember { mutableStateOf(false) }

    LaunchedEffect(hasAccounts) {
        if (!hasAccounts) {
            if (navController.currentDestination?.route != Routes.SIGN_IN) {
                navController.navigate(Routes.SIGN_IN) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } else if (navController.currentDestination?.route == Routes.SIGN_IN) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.SIGN_IN) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(sessionState.session.isActive) {
        if (sessionState.session.isActive && !wasSessionActive) {
            android.util.Log.d(
                "APBFit_Run",
                "Nav: session started, pushing ActiveRuns sessionId=${sessionState.session.sessionId}",
            )
            navController.navigate(Routes.ACTIVE_RUN) {
                launchSingleTop = true
            }
        }
        wasSessionActive = sessionState.session.isActive
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
        composable(Routes.ACTIVE_RUN) {
            ActiveRunScreen(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
