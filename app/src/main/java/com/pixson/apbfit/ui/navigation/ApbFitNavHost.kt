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

    LaunchedEffect(activeAccount) {
        val destination = if (activeAccount == null) Routes.SIGN_IN else Routes.HOME
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
        composable(Routes.HOME) { HomeScreen() }
        composable(Routes.ACTIVE_RUN) { ActiveRunScreen() }
        composable(Routes.HISTORY) { HistoryScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
