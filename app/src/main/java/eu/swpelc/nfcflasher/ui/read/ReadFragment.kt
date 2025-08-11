package eu.swpelc.nfcflasher.ui.read

import android.nfc.NdefMessage
import android.nfc.NdefRecord // Make sure NdefRecord is imported
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import eu.swpelc.nfcflasher.BuildingType
import eu.swpelc.nfcflasher.data.ConfigRepository
import eu.swpelc.nfcflasher.databinding.FragmentReadBinding
import eu.swpelc.nfcflasher.viewmodel.SharedViewModel
import java.io.IOException
import java.nio.charset.Charset // For comparing record type "B"

class ReadFragment : Fragment() {

    private var _binding: FragmentReadBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var configRepository: ConfigRepository

    companion object {
        private const val TAG = "ReadFragmentNFC"
        // Define expected type for TNF_WELL_KNOWN record
        private val EXPECTED_RECORD_TYPE = "B".toByteArray(Charsets.US_ASCII)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configRepository = ConfigRepository(requireContext())

        sharedViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            Log.d(TAG, "Observer triggered. Tag: ${tag?.toString()}")
            tag?.let {
                Log.d(TAG, "processNfcTag called with Tag: ${it.toString()}")
                processNfcTag(it)
                sharedViewModel.tagProcessed() // Notify ViewModel that tag has been handled
            }
        }
    }

    private fun processNfcTag(tag: Tag) {
        Log.d(TAG, "Inside processNfcTag. Tag: ${tag.toString()}")
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Log.w(TAG, "Tag does not support NDEF.")
            binding.textReadRawData.text = "Raw Data: Not NDEF"
            binding.textReadBuildingName.text = "Building: -"
            Toast.makeText(context, "Tag is not NDEF formatted.", Toast.LENGTH_SHORT).show()
            return
        }

        var connectionLost = false
        try {
            ndef.connect()
            val ndefMessage: NdefMessage? = try {
                ndef.cachedNdefMessage ?: ndef.ndefMessage
            } catch (e: Exception) { // Catch potential errors during message retrieval like tag lost
                Log.e(TAG, "Error getting NDEF message: ${e.message}")
                connectionLost = true
                null
            }

            if (connectionLost || ndefMessage == null) {
                val message = if (connectionLost) "Error reading NDEF message from tag." else "No NDEF message found on tag."
                binding.textReadRawData.text = if (connectionLost) "Raw Data: Error reading tag" else "Raw Data: No NDEF message"
                binding.textReadBuildingName.text = "Building: -"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return // NDEF close will be handled in finally
            }

            if (ndefMessage.records.isNotEmpty()) {
                val record = ndefMessage.records[0]
                Log.d(TAG, "Processing record 0: TNF=${record.tnf}, Type=${String(record.type, Charsets.US_ASCII)}, Payload Length=${record.payload.size}")

                // Check for TNF_WELL_KNOWN and if the record type is "B"
                if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(EXPECTED_RECORD_TYPE)) {
                    if (record.payload.isNotEmpty()) {
                        val buildingByte = record.payload[0] // Our data is the first byte
                        binding.textReadRawData.text = "Raw Data: 0x${buildingByte.toUByte().toString(16).padStart(2, '0').uppercase()}"
                        Log.d(TAG, "Extracted byte: $buildingByte from 'B' type record.")

                        var foundBuildingType: BuildingType? = null
                        // Iterate through all BuildingType enum entries to find a match with the effective value
                        for (typeEntry in BuildingType.entries) {
                            val customValue = configRepository.getCustomValue(typeEntry)
                            val effectiveValue = customValue ?: typeEntry.byteValue // Check custom override first, then default
                            if (effectiveValue == buildingByte) {
                                foundBuildingType = typeEntry
                                break // Found a match
                            }
                        }

                        if (foundBuildingType != null) {
                            Log.i(TAG, "Found BuildingType (considering overrides): ${foundBuildingType.name}")
                            binding.textReadBuildingName.text = "Building: ${foundBuildingType.name}"
                            Toast.makeText(context, "Read: ${foundBuildingType.name}", Toast.LENGTH_LONG).show()
                        } else {
                            Log.w(TAG, "Unknown building byte value (considering overrides): $buildingByte")
                            binding.textReadBuildingName.text = "Building: Unknown Value"
                            Toast.makeText(context, "Read unknown byte value: 0x${buildingByte.toUByte().toString(16).uppercase()}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w(TAG, "Record 'B' type payload is empty.")
                        binding.textReadRawData.text = "Raw Data: Empty 'B' payload"
                        binding.textReadBuildingName.text = "Building: -"
                        Toast.makeText(context, "NDEF 'B' record payload is empty.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "Record is not the expected 'B' type or TNF. TNF=${record.tnf}, Type=${String(record.type, Charsets.US_ASCII)}")
                    binding.textReadRawData.text = "Raw Data: Not a valid building tag"
                    binding.textReadBuildingName.text = "Building: -"
                    Toast.makeText(context, "Tag does not contain valid building data.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "NDEF message contains no records.")
                binding.textReadRawData.text = "Raw Data: No records in message"
                binding.textReadBuildingName.text = "Building: -"
                Toast.makeText(context, "NDEF message contains no records.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            connectionLost = true
            Log.e(TAG, "SecurityException: Tag out of date or permission issue.", e)
            binding.textReadRawData.text = "Raw Data: Tag Error"
            binding.textReadBuildingName.text = "Building: -"
            Toast.makeText(context, "NFC Tag connection lost. Please remove and re-tap the tag.", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            connectionLost = true // Potentially, an IO error can also mean the tag is gone
            Log.e(TAG, "IOException while reading NDEF tag", e)
            binding.textReadRawData.text = "Raw Data: Error"
            binding.textReadBuildingName.text = "Building: -"
            Toast.makeText(context, "Error reading tag: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            connectionLost = true // Treat other exceptions as potentially losing the tag too
            Log.e(TAG, "Exception while reading NDEF tag", e)
            binding.textReadRawData.text = "Raw Data: Error"
            binding.textReadBuildingName.text = "Building: -"
            Toast.makeText(context, "An unexpected error occurred: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            if (ndef.isConnected && !connectionLost) {
                try {
                    ndef.close()
                    Log.d(TAG, "NDEF connection closed in finally block.")
                } catch (e: IOException) {
                    Log.e(TAG, "IOException closing NDEF in finally block.", e)
                }
            } else if (connectionLost) { // ndef != null is implied here
                Log.d(TAG, "NDEF connection was lost or not closed due to prior error.")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear the binding when the view is destroyed
        Log.d(TAG, "onDestroyView called.")
    }
}
