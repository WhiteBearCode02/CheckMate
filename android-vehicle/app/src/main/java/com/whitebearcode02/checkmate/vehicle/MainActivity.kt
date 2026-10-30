
package com.whitebearcode02.checkmate.vehicle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitebearcode02.checkmate.vehicle.ui.screen.CheckMateApp
import com.whitebearcode02.checkmate.vehicle.ui.theme.CheckMateVehicleTheme
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateViewModel
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(
            this, CheckMateViewModelFactory(applicationContext, developerMode = BuildConfig.DEBUG),
        )[CheckMateViewModel::class.java]
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                viewModel.onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                viewModel.onBackground()
            }
        })
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            CheckMateVehicleTheme {
                CheckMateApp(state, viewModel.actions, developerMode = BuildConfig.DEBUG)
            }
        }
    }
}
