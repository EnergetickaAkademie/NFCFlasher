package eu.swpelc.nfcflasher.data

import android.content.Context
import android.content.SharedPreferences
import eu.swpelc.nfcflasher.BuildingType
import eu.swpelc.nfcflasher.nfc.ProtocolMode

class ConfigRepository(context: Context) {

    private val prefsName = "NfcBuildingConfig"
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        // Prefix to avoid key collisions if SharedPreferences is used for other things
        private const val VALUE_PREFIX = "config_value_"
        private const val PROTOCOL_MODE_KEY = "protocol_mode"
    }

    /**
     * Retrieves the custom value for a given BuildingType from SharedPreferences.
     * Returns null if no custom value is set.
     */
    fun getCustomValue(buildingType: BuildingType): Byte? {
        val key = VALUE_PREFIX + buildingType.name
        // SharedPreferences doesn't have a getByte, so we use getInt and cast.
        // If the key doesn't exist, contains() is false.
        return if (sharedPreferences.contains(key)) {
            sharedPreferences.getInt(key, buildingType.byteValue.toInt()).toByte()
        } else {
            null
        }
    }

    /**
     * Sets a custom value for a given BuildingType in SharedPreferences.
     */
    fun setCustomValue(buildingType: BuildingType, value: Byte) {
        val key = VALUE_PREFIX + buildingType.name
        // SharedPreferences doesn't have putByte, so we use putInt.
        sharedPreferences.edit().putInt(key, value.toInt()).commit()
    }

    fun getProtocolMode(): ProtocolMode {
        val savedMode = sharedPreferences.getString(PROTOCOL_MODE_KEY, null)
        return ProtocolMode.entries.firstOrNull { it.name == savedMode } ?: ProtocolMode.V2
    }

    fun setProtocolMode(mode: ProtocolMode) {
        sharedPreferences.edit().putString(PROTOCOL_MODE_KEY, mode.name).apply()
    }

    /**
     * Removes the custom value for a given BuildingType from SharedPreferences,
     * effectively resetting it to its default.
     */
    fun resetValue(buildingType: BuildingType) {
        val key = VALUE_PREFIX + buildingType.name
        sharedPreferences.edit().remove(key).commit()
    }

    /**
     * Retrieves all currently set custom values.
     * Useful for diagnostics or if needed by the ViewModel directly.
     */
    fun getAllCustomValues(): Map<String, Byte> {
        val customValues = mutableMapOf<String, Byte>()
        BuildingType.values().forEach { buildingType ->
            getCustomValue(buildingType)?.let {
                customValues[buildingType.name] = it
            }
        }
        return customValues
    }

    /**
     * Removes all custom values from SharedPreferences, resetting all types to their defaults.
     */
    fun resetAllToDefaults() {
        val editor = sharedPreferences.edit()
        BuildingType.values().forEach { buildingType ->
            editor.remove(VALUE_PREFIX + buildingType.name)
        }
        editor.commit()
    }
}
