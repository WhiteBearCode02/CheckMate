package com.whitebearcode02.checkmate.vehicle.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.whitebearcode02.checkmate.vehicle.data.provider.AndroidPleosSdkSession
import com.whitebearcode02.checkmate.vehicle.data.provider.PleosVehicleProvider
import com.whitebearcode02.checkmate.vehicle.data.provider.VehicleProviderFactory
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource

/** The application context is retained; Activities and Compose never access the Vehicle SDK. */
class CheckMateViewModelFactory(context: Context, private val developerMode: Boolean) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == CheckMateViewModel::class.java)
        val providers = VehicleProviderFactory(
            createPleos = { PleosVehicleProvider(AndroidPleosSdkSession(applicationContext)) },
            allowMock = developerMode,
        )
        return CheckMateViewModel(
            provider = providers.create(DataSource.PLEOS),
            developerMode = developerMode,
            providerFactory = providers::create,
        ) as T
    }
}
