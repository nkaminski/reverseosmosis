package io.kaminski.reverseosmosis

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class WaterDispenserViewModel(application: Application) : AndroidViewModel(application) {
    val dataStoreManager = DataStoreManager(application)
    val bleManager = WaterDispenserBleManager(application, dataStoreManager)

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
