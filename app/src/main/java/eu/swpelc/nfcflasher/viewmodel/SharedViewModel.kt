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
}
