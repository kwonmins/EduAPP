package com.example.myhealth

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myhealth.ui.MoodilyAppScreen

@Composable
fun AppNav(nav: NavHostController) {
    NavHost(navController = nav, startDestination = "moodily") {
        composable("moodily") {
            MoodilyAppScreen()
        }
    }
}
