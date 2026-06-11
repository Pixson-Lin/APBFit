package com.pixson.apbfit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pixson.apbfit.ui.screen.ActiveRunScreen
import com.pixson.apbfit.ui.screen.HistoryScreen
import com.pixson.apbfit.ui.screen.HomeScreen
import com.pixson.apbfit.ui.screen.SettingsScreen
import com.pixson.apbfit.ui.screen.SignInScreen

@Composable
fun ApbFitNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.HOME,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.SIGN_IN) { SignInScreen() }
        composable(Routes.HOME) { HomeScreen() }
        composable(Routes.ACTIVE_RUN) { ActiveRunScreen() }
        composable(Routes.HISTORY) { HistoryScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
