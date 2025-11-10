package com.example.myhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.example.myhealth.ui.theme.IvoryTheme   // ← 여기만 바뀜

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IvoryTheme {                         // ← MyHealthTheme → IvoryTheme
                val nav = rememberNavController()
                AppNav(nav)
            }
        }
    }
}
