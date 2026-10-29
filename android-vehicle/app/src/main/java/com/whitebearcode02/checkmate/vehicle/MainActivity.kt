
package com.whitebearcode02.checkmate.vehicle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitebearcode02.checkmate.vehicle.data.provider.MockVehicleProvider
import com.whitebearcode02.checkmate.vehicle.data.provider.PleosVehicleProvider
import com.whitebearcode02.checkmate.vehicle.ui.screen.CheckMateApp
import com.whitebearcode02.checkmate.vehicle.ui.theme.CheckMateVehicleTheme
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(CheckMateViewModel::class.java))
                return CheckMateViewModel(
                    provider = if (BuildConfig.DEBUG) MockVehicleProvider() else PleosVehicleProvider(),
                    developerMode = BuildConfig.DEBUG,
                ) as T
            }
        })[CheckMateViewModel::class.java]
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            CheckMateVehicleTheme {
                CheckMateApp(state, viewModel.actions, developerMode = BuildConfig.DEBUG)
            }
        }
    }
}
