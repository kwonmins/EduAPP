package com.example.myhealth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.myhealth.session.AuthState
import com.example.myhealth.session.AuthViewModel
import com.example.myhealth.session.ApiConst

@Composable
fun SplashScreen(nav: NavHostController, authVm: AuthViewModel) {
    val state by authVm.authState.collectAsState()

    LaunchedEffect(state) {
        // ✅ 개발용: 강제로 로그인 화면부터
        if (ApiConst.FORCE_LOGIN_ON_START) {
            nav.navigate("login") { popUpTo("splash") { inclusive = true } }
            return@LaunchedEffect
        }
        // 정상 플로우: 세션 있으면 홈, 없으면 로그인
        when (state) {
            AuthState.Unauthenticated ->
                nav.navigate("login") { popUpTo("splash") { inclusive = true } }
            is AuthState.Authenticated ->
                nav.navigate("home") { popUpTo("splash") { inclusive = true } }
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
