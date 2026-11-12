package eu.swpelc.nfcflasher.viewmodel

import android.nfc.Tag
import android.util.Log // Import Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {

    companion object { // Add a companion object for the TAG
        private const val TAG = "SharedViewModelNFC"
    }

    private val _nfcTag = MutableLiveData<Tag?>()
    val nfcTag: LiveData<Tag?> = _nfcTag

    // Persisted read result so UI survives rotation
    private val _lastReadRawData = MutableLiveData<String?>()
    val lastReadRawData: LiveData<String?> = _lastReadRawData

    private val _lastReadTagName = MutableLiveData<String?>()
    val lastReadTagName: LiveData<String?> = _lastReadTagName

    private val _lastReadBuildingName = MutableLiveData<String?>()
    val lastReadBuildingName: LiveData<String?> = _lastReadBuildingName

    private val _lastReadProtocol = MutableLiveData<String?>()
    val lastReadProtocol: LiveData<String?> = _lastReadProtocol

    // Persisted write UI state
    private val _writeActive = MutableLiveData<Boolean>(false)
    val writeActive: LiveData<Boolean> = _writeActive

    private val _writeSelectedTypeName = MutableLiveData<String?>()
    val writeSelectedTypeName: LiveData<String?> = _writeSelectedTypeName

    private val _writeSelectedValue = MutableLiveData<Byte?>()
    val writeSelectedValue: LiveData<Byte?> = _writeSelectedValue

    // Called by MainActivity when a new tag is detected
    fun setNfcTag(newTag: Tag?) {
        Log.d(TAG, "setNfcTag called. New tag: ${newTag?.toString()}") // Log when tag is set
        _nfcTag.value = newTag
    }

    // Called by fragments after they have processed the tag
    // to prevent reprocessing on configuration change or re-navigation
    fun tagProcessed() {
        Log.d(TAG, "tagProcessed called. Clearing tag. Current tag was: ${_nfcTag.value?.toString()}") // Log when tag is cleared
        _nfcTag.value = null
    }

    // Helper to persist last read value
    fun setLastRead(rawData: String, tagName: String, protocol: String, buildingName: String? = null) {
        Log.d(TAG, "setLastRead called. raw=$rawData, tag=$tagName, protocol=$protocol")
        _lastReadRawData.value = rawData
        _lastReadTagName.value = tagName
        _lastReadBuildingName.value = buildingName
        _lastReadProtocol.value = protocol
    }

    // Helpers for write UI state
    fun setWriteActive(active: Boolean) {
        Log.d(TAG, "setWriteActive: $active")
        _writeActive.value = active
    }

    fun setWriteSelection(typeName: String?, value: Byte?) {
        Log.d(TAG, "setWriteSelection: name=$typeName value=${value?.toUByte()?.toString(16)})")
        _writeSelectedTypeName.value = typeName
        _writeSelectedValue.value = value
    }
}
