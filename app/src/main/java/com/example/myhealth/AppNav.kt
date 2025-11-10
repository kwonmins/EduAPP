package com.example.myhealth

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myhealth.session.AuthViewModel
import com.example.myhealth.session.DailySessionViewModel
import com.example.myhealth.ui.*   // SplashScreen, LoginScreen, HomeScreen, WordChainScreen, MemoryDiaryScreen, MandalaScreen, SummaryScreen, HistoryCalendarScreen

@Composable
fun AppNav(nav: NavHostController) {
    val dailyVm: DailySessionViewModel = viewModel()
    val authVm: AuthViewModel = viewModel()

    NavHost(navController = nav, startDestination = "splash") {
        composable("splash") { SplashScreen(nav = nav, authVm = authVm) }

        composable("login") {
            LoginScreen(
                onLoginDone = {
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onStart = { nav.navigate("word") },
                onLogout = {
                    authVm.logout()
                    nav.navigate("login") { popUpTo("home") { inclusive = true } }
                }
            )
        }

        composable("word")    { WordChainScreen(vm = dailyVm, onDone = { nav.navigate("diary") }) }
        composable("diary")   { MemoryDiaryScreen(onDone = { nav.navigate("mandala") }) }
        composable("mandala") { MandalaScreen(vm = dailyVm, onDone = { nav.navigate("summary") }) }

        composable("summary") {
            SummaryCalendarScreen()
        }
       // composable("history") { HistoryCalendarScreen(onBack = { nav.popBackStack() }) }
    }
}
