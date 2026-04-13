package com.loic.wakeup.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.loic.wakeup.ui.screens.AlarmEditScreen
import com.loic.wakeup.ui.screens.AlarmListScreen
import com.loic.wakeup.ui.screens.NfcSettingsScreen

sealed class Screen(val route: String) {
    object AlarmList   : Screen("alarm_list")
    object AlarmEdit   : Screen("alarm_edit/{alarmId}") {
        fun createRoute(alarmId: Int = -1) = "alarm_edit/$alarmId"
    }
    object NfcSettings : Screen("nfc_settings")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.AlarmList.route) {
        composable(Screen.AlarmList.route) {
            AlarmListScreen(
                onAdd   = { navController.navigate(Screen.AlarmEdit.createRoute()) },
                onEdit  = { id -> navController.navigate(Screen.AlarmEdit.createRoute(id)) },
                onSettings = { navController.navigate(Screen.NfcSettings.route) }
            )
        }
        composable(
            route = Screen.AlarmEdit.route,
            arguments = listOf(navArgument("alarmId") { type = NavType.IntType; defaultValue = -1 })
        ) { backStack ->
            val alarmId = backStack.arguments?.getInt("alarmId") ?: -1
            AlarmEditScreen(
                alarmId = if (alarmId == -1) null else alarmId,
                onDone  = { navController.popBackStack() }
            )
        }
        composable(Screen.NfcSettings.route) {
            NfcSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
