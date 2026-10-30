package com.whitebearcode02.checkmate.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.whitebearcode02.checkmate.mobile.ui.screen.HomeScreen
import com.whitebearcode02.checkmate.mobile.ui.theme.CheckMateMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheckMateMobileTheme {
                HomeScreen()
            }
        }
    }
}
