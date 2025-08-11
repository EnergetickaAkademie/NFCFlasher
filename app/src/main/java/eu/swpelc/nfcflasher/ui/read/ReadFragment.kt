package eu.swpelc.nfcflasher.ui.read

import android.nfc.NdefMessage
import android.nfc.NdefRecord
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
import eu.swpelc.nfcflasher.data.ConfigRepository // Import added
import eu.swpelc.nfcflasher.databinding.FragmentReadBinding
import eu.swpelc.nfcflasher.viewmodel.SharedViewModel
import java.io.IOException

class ReadFragment : Fragment() {

    private var _binding: FragmentReadBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var configRepository: ConfigRepository // Declaration added

    companion object {
        private const val TAG = "ReadFragmentNFC"
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

        configRepository = ConfigRepository(requireContext()) // Initialization

        sharedViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            Log.d(TAG, "Observer triggered. Tag: ${tag?.toString()}")
            tag?.let {
                Log.d(TAG, "processNfcTag called with Tag: ${it.toString()}")
                processNfcTag(it)
                sharedViewModel.tagProcessed()
            }
        }
    }

    private fun processNfcTag(tag: Tag) {
        Log.d(TAG, "Inside processNfcTag. Tag: ${tag.toString()}")
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Log.w(TAG, "NDEF tag is null.")
            binding.textReadRawData.text = "Raw Data: Not NDEF"
            binding.textReadBuildingName.text = "Building: -"
            Toast.makeText(context, "Tag is not NDEF formatted.", Toast.LENGTH_SHORT).show()
            return
        }

        var connectionLost = false // For robust NDEF close
        try {
            Log.d(TAG, "Connecting to NDEF tag.")
            ndef.connect()
            Log.d(TAG, "NDEF connected.")

            val ndefMessage: NdefMessage? = try {
                ndef.cachedNdefMessage ?: ndef.ndefMessage
            } catch (e: Exception) { // Catch potential errors during message retrieval like tag lost
                Log.e(TAG, "Error getting NDEF message: ${e.message}")
                connectionLost = true
                null
            }
            Log.d(TAG, "NDEF message: ${ndefMessage?.toString()}")

            if (connectionLost || ndefMessage == null) {
                if (!connectionLost) { // Only show this toast if not already handled as connectionLost
                    Log.w(TAG, "NDEF message is null.")
                    binding.textReadRawData.text = "Raw Data: No NDEF message"
                    binding.textReadBuildingName.text = "Building: -"
                    Toast.makeText(context, "No NDEF message found on tag.", Toast.LENGTH_SHORT).show()
                } else {
                    binding.textReadRawData.text = "Raw Data: Error reading tag"
                    binding.textReadBuildingName.text = "Building: -"
                    Toast.makeText(context, "Error reading NDEF message from tag.", Toast.LENGTH_SHORT).show()
                }
                // ndef.close() will be handled in finally
                return
            }

            Log.d(TAG, "NDEF records count: ${ndefMessage.records.size}")
            if (ndefMessage.records.isNotEmpty()) {
                val record = ndefMessage.records[0]
                val isTypeB = record.type.contentEquals("B".toByteArray(Charsets.US_ASCII))
                val isTnfWellKnown = record.tnf == NdefRecord.TNF_WELL_KNOWN

                if (isTypeB && isTnfWellKnown && record.payload.isNotEmpty()) {
                    val buildingByte = record.payload[0]
                    binding.textReadRawData.text = "Raw Data: 0x${buildingByte.toUByte().toString(16).padStart(2, '0').uppercase()}"

                    var foundBuildingType: BuildingType? = null
                    for (typeEntry in BuildingType.entries) {
                        val customValue = configRepository.getCustomValue(typeEntry)
                        val effectiveValue = customValue ?: typeEntry.byteValue
                        if (effectiveValue == buildingByte) {
                            foundBuildingType = typeEntry
                            break
                        }
                    }

                    if (foundBuildingType != null) {
                        binding.textReadBuildingName.text = "Building: ${foundBuildingType.name}"
                        Toast.makeText(context, "Read: ${foundBuildingType.name}", Toast.LENGTH_LONG).show()
                    } else {
                        binding.textReadBuildingName.text = "Building: Unknown Value"
                        Toast.makeText(context, "Read unknown byte value: 0x${buildingByte.toUByte().toString(16).uppercase()}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    binding.textReadRawData.text = "Raw Data: Not a valid building tag"
                    binding.textReadBuildingName.text = "Building: -"
                    Toast.makeText(context, "Tag is not a valid building type record.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "NDEF message contains no records.")
                binding.textReadRawData.text = "Raw Data: No records in message"
                binding.textReadBuildingName.text = "Building: -"
                Toast.makeText(context, "NDEF message contains no records.", Toast.LENGTH_SHORT).show()
            }
            // ndef.close() will be handled in finally
        } catch (e: SecurityException) {
            connectionLost = true
            Log.e(TAG, "SecurityException: Tag out of date or permission issue.", e)
            binding.textReadRawData.text = "Raw Data: Tag Error"
            binding.textReadBuildingName.text = "Building: -"
            Toast.makeText(context, "NFC Tag connection lost. Please remove and re-tap the tag.", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            connectionLost = true; // Potentially, an IO error can also mean the tag is gone
            Log.e(TAG, "IOException while reading NDEF tag", e)
            binding.textReadRawData.text = "Raw Data: Error"
            binding.textReadBuildingName.text = "Building: -"
            Toast.makeText(context, "Error reading tag: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            connectionLost = true; // Treat other exceptions as potentially losing the tag too
            Log.e(TAG, "Exception while reading NDEF tag", e)
            binding.textReadRawData.text = "Raw Data: Error"
            binding.textReadBuildingName.text = "Building: -"
            Toast.makeText(context, "An unexpected error occurred: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            if (ndef != null && ndef.isConnected && !connectionLost) {
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
        _binding = null
        Log.d(TAG, "onDestroyView called.")
    }
}
