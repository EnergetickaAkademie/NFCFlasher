package eu.swpelc.nfcflasher.ui.config

data class BuildingConfigDisplayItem(
    val typeName: String, // Name of the BuildingType enum constant, e.g., "FARM"
    var effectiveValue: Byte, // The current value to be used (custom or default)
    val isOverridden: Boolean, // True if the effectiveValue is from SharedPreferences (custom)
    val defaultValue: Byte // Original default value from the BuildingType enum
)

