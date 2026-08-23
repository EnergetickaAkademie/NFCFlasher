package eu.swpelc.nfcflasher.ui.read

import android.nfc.NdefMessage
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
import eu.swpelc.nfcflasher.nfc.EnakNfcProtocol
import eu.swpelc.nfcflasher.viewmodel.SharedViewModel
import java.io.IOException

class ReadFragment : Fragment() {

    private var _binding: FragmentReadBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var configRepository: ConfigRepository

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
        configRepository = ConfigRepository(requireContext())

        sharedViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            Log.d(TAG, "Observer triggered. Tag: ${tag?.toString()}")
            if (tag != null) {
                Log.d(TAG, "processNfcTag called with Tag: ${tag.toString()}")
                processNfcTag(tag)
                // Do not clear persisted read immediately; keep last value for rotation
                sharedViewModel.setNfcTag(null)
            } else {
                Log.d(TAG, "Tag is null in observer")
            }
        }

        // Restore last read values on rotation
        sharedViewModel.lastReadByte.observe(viewLifecycleOwner) { byteVal ->
            if (byteVal != null) {
                binding.textReadRawData.text = "Raw Data: 0x${byteVal.toUByte().toString(16).padStart(2, '0').uppercase()}"
            }
        }
        sharedViewModel.lastReadName.observe(viewLifecycleOwner) { name ->
            binding.textReadBuildingName.text = name?.let { "Building: $it" } ?: "Building: -"
        }
        sharedViewModel.lastReadProtocol.observe(viewLifecycleOwner) { protocol ->
            binding.textReadProtocol.text = protocol?.let { "Protocol: $it" } ?: "Protocol: -"
        }
    }

    private fun processNfcTag(tag: Tag) {
        Log.d(TAG, "Inside processNfcTag. Tag: ${tag.toString()}")
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Log.w(TAG, "Tag does not support NDEF.")
            binding.textReadRawData.text = "Raw Data: Not NDEF"
            binding.textReadBuildingName.text = "Building: -"
            binding.textReadProtocol.text = "Protocol: -"
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
                binding.textReadProtocol.text = "Protocol: -"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return // NDEF close will be handled in finally
            }

            if (ndefMessage.records.isNotEmpty()) {
                val record = ndefMessage.records[0]
                Log.d(TAG, "Processing record 0: TNF=${record.tnf}, Type=${String(record.type, Charsets.US_ASCII)}, Payload Length=${record.payload.size}")

                val decoded = EnakNfcProtocol.decodeBuilding(record.tnf, record.type, record.payload)
                if (decoded != null) {
                    val buildingByte = decoded.buildingType
                    val protocolName = decoded.protocolMode.displayName
                    binding.textReadRawData.text = "Raw Data: 0x${buildingByte.toUByte().toString(16).padStart(2, '0').uppercase()}"
                    binding.textReadProtocol.text = "Protocol: $protocolName"

                    val foundBuildingType = BuildingType.entries.firstOrNull { typeEntry ->
                        val customValue = configRepository.getCustomValue(typeEntry)
                        (customValue ?: typeEntry.byteValue) == buildingByte
                    }

                    if (foundBuildingType != null) {
                        Log.i(TAG, "Found ${foundBuildingType.name} using $protocolName")
                        binding.textReadBuildingName.text = "Building: ${foundBuildingType.name}"
                        Toast.makeText(context, "Read: ${foundBuildingType.name} ($protocolName)", Toast.LENGTH_LONG).show()
                        sharedViewModel.setLastRead(buildingByte, foundBuildingType.name, protocolName)
                    } else {
                        Log.w(TAG, "Unknown building byte value: $buildingByte")
                        binding.textReadBuildingName.text = "Building: Unknown Value"
                        Toast.makeText(context, "Read unknown byte value: 0x${buildingByte.toUByte().toString(16).uppercase()}", Toast.LENGTH_SHORT).show()
                        sharedViewModel.setLastRead(buildingByte, null, protocolName)
                    }
                } else {
                    Log.w(TAG, "Record is not a valid v2 or legacy building tag. TNF=${record.tnf}, Type=${String(record.type, Charsets.US_ASCII)}")
                    binding.textReadRawData.text = "Raw Data: Not a valid building tag"
                    binding.textReadBuildingName.text = "Building: -"
                    binding.textReadProtocol.text = "Protocol: -"
                    Toast.makeText(context, "Tag does not contain valid building data.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "NDEF message contains no records.")
                binding.textReadRawData.text = "Raw Data: No records in message"
                binding.textReadBuildingName.text = "Building: -"
                binding.textReadProtocol.text = "Protocol: -"
                Toast.makeText(context, "NDEF message contains no records.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            connectionLost = true
            Log.e(TAG, "SecurityException: Tag out of date or permission issue.", e)
            binding.textReadRawData.text = "Raw Data: Tag Error"
            binding.textReadBuildingName.text = "Building: -"
            binding.textReadProtocol.text = "Protocol: -"
            Toast.makeText(context, "NFC Tag connection lost. Please remove and re-tap the tag.", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            connectionLost = true // Potentially, an IO error can also mean the tag is gone
            Log.e(TAG, "IOException while reading NDEF tag", e)
            binding.textReadRawData.text = "Raw Data: Error"
            binding.textReadBuildingName.text = "Building: -"
            binding.textReadProtocol.text = "Protocol: -"
            Toast.makeText(context, "Error reading tag: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            connectionLost = true // Treat other exceptions as potentially losing the tag too
            Log.e(TAG, "Exception while reading NDEF tag", e)
            binding.textReadRawData.text = "Raw Data: Error"
            binding.textReadBuildingName.text = "Building: -"
            binding.textReadProtocol.text = "Protocol: -"
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
