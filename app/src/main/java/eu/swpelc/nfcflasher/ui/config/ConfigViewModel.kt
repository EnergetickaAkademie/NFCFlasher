package eu.swpelc.nfcflasher.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import eu.swpelc.nfcflasher.BuildingType
import eu.swpelc.nfcflasher.data.ConfigRepository

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ConfigRepository = ConfigRepository(application)

    private val _configItems = MutableLiveData<List<BuildingConfigDisplayItem>>()
    val configItems: LiveData<List<BuildingConfigDisplayItem>> = _configItems

    init {
        loadConfiguration()
    }

    fun loadConfiguration() {
        val items = BuildingType.values().map { buildingType ->
            val customValue = repository.getCustomValue(buildingType)
            BuildingConfigDisplayItem(
                typeName = buildingType.name,
                effectiveValue = customValue ?: buildingType.byteValue,
                isOverridden = customValue != null,
                defaultValue = buildingType.byteValue
            )
        }
        _configItems.value = items
    }

    fun updateBuildingValue(typeName: String, newValue: Byte) {
        val buildingType = BuildingType.valueOf(typeName) // Can throw IllegalArgumentException if typeName is invalid
        repository.setCustomValue(buildingType, newValue)
        loadConfiguration() // Reload to reflect changes
    }

    fun resetBuildingValue(typeName: String) {
        val buildingType = BuildingType.valueOf(typeName)
        repository.resetValue(buildingType)
        loadConfiguration() // Reload
    }

    fun resetAllToDefaults() {
        repository.resetAllToDefaults()
        loadConfiguration() // Reload
    }
}