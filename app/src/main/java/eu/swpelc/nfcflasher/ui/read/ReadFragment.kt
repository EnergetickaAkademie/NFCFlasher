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
import eu.swpelc.nfcflasher.nfc.EnakTagKind
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
        sharedViewModel.lastReadRawData.observe(viewLifecycleOwner) { rawData ->
            binding.textReadRawData.text = rawData?.let { "Raw Data: $it" } ?: "Raw Data: -"
        }
        sharedViewModel.lastReadTagName.observe(viewLifecycleOwner) { name ->
            binding.textReadTagName.text = name?.let { "Tag: $it" } ?: "Tag: -"
        }
        sharedViewModel.lastReadBuildingName.observe(viewLifecycleOwner) { name ->
            showBuildingImage(name?.let { buildingName ->
                BuildingType.entries.firstOrNull { it.name == buildingName }
            })
        }
        sharedViewModel.lastReadProtocol.observe(viewLifecycleOwner) { protocol ->
            binding.textReadProtocol.text = protocol?.let { "Protocol: $it" } ?: "Protocol: -"
        }
    }

    private fun processNfcTag(tag: Tag) {
        Log.d(TAG, "Inside processNfcTag. Tag: ${tag.toString()}")
        showBuildingImage(null)
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Log.w(TAG, "Tag does not support NDEF.")
            binding.textReadRawData.text = "Raw Data: Not NDEF"
            binding.textReadTagName.text = "Tag: Non-NDEF tag"
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
                binding.textReadTagName.text = "Tag: -"
                binding.textReadProtocol.text = "Protocol: -"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return // NDEF close will be handled in finally
            }

            if (ndefMessage.records.isNotEmpty()) {
                val record = ndefMessage.records[0]
                Log.d(TAG, "Processing record 0: TNF=${record.tnf}, Type=${String(record.type, Charsets.US_ASCII)}, Payload Length=${record.payload.size}")

                val decoded = EnakNfcProtocol.decodeTag(record.tnf, record.type, record.payload)
                if (decoded != null) {
                    val protocolName = decoded.protocolMode.displayName
                    val rawData = record.payload.toHexString()
                    binding.textReadRawData.text = "Raw Data: $rawData"
                    binding.textReadProtocol.text = "Protocol: $protocolName"

                    if (decoded.kind == EnakTagKind.BUILDING) {
                        val buildingByte = requireNotNull(decoded.buildingType)
                        val foundBuildingType = BuildingType.entries.firstOrNull { typeEntry ->
                            val customValue = configRepository.getCustomValue(typeEntry)
                            (customValue ?: typeEntry.byteValue) == buildingByte
                        }

                        val tagName = foundBuildingType?.let { "Building: ${it.name}" }
                            ?: "Building: Unknown Value"
                        showBuildingImage(foundBuildingType)
                        binding.textReadTagName.text = "Tag: $tagName"
                        Toast.makeText(context, "Read: $tagName ($protocolName)", Toast.LENGTH_LONG).show()
                        sharedViewModel.setLastRead(rawData, tagName, protocolName, foundBuildingType?.name)
                    } else {
                        val tagName = decoded.kind.displayName
                        Log.i(TAG, "Found $tagName using $protocolName")
                        binding.textReadTagName.text = "Tag: $tagName"
                        Toast.makeText(context, "Read: $tagName ($protocolName)", Toast.LENGTH_LONG).show()
                        sharedViewModel.setLastRead(rawData, tagName, protocolName)
                    }
                } else {
                    val recordType = record.type.toDisplayString()
                    val rawData = record.payload.toHexString()
                    Log.i(TAG, "Unrecognized NDEF record. TNF=${record.tnf}, Type=$recordType")
                    binding.textReadRawData.text = "Raw Data: $rawData"
                    binding.textReadTagName.text = "Tag: NDEF record ($recordType)"
                    binding.textReadProtocol.text = "Protocol: Unknown"
                    Toast.makeText(context, "Read NDEF tag: $recordType", Toast.LENGTH_SHORT).show()
                    sharedViewModel.setLastRead(rawData, "NDEF record ($recordType)", "Unknown")
                }
            } else {
                Log.w(TAG, "NDEF message contains no records.")
                binding.textReadRawData.text = "Raw Data: No records in message"
                binding.textReadTagName.text = "Tag: Empty NDEF tag"
                binding.textReadProtocol.text = "Protocol: -"
                Toast.makeText(context, "NDEF message contains no records.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            connectionLost = true
            Log.e(TAG, "SecurityException: Tag out of date or permission issue.", e)
            binding.textReadRawData.text = "Raw Data: Tag Error"
            binding.textReadTagName.text = "Tag: -"
            binding.textReadProtocol.text = "Protocol: -"
            Toast.makeText(context, "NFC Tag connection lost. Please remove and re-tap the tag.", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            connectionLost = true // Potentially, an IO error can also mean the tag is gone
            Log.e(TAG, "IOException while reading NDEF tag", e)
            binding.textReadRawData.text = "Raw Data: Error"
            binding.textReadTagName.text = "Tag: -"
            binding.textReadProtocol.text = "Protocol: -"
            Toast.makeText(context, "Error reading tag: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            connectionLost = true // Treat other exceptions as potentially losing the tag too
            Log.e(TAG, "Exception while reading NDEF tag", e)
            binding.textReadRawData.text = "Raw Data: Error"
            binding.textReadTagName.text = "Tag: -"
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

    private fun ByteArray.toHexString(): String =
        if (isEmpty()) "(empty)" else joinToString(" ") {
            "0x${it.toUByte().toString(16).padStart(2, '0').uppercase()}"
        }

    private fun ByteArray.toDisplayString(): String =
        toString(Charsets.US_ASCII).takeIf { text ->
            text.isNotBlank() && text.all { character -> character.code in 0x20..0x7e }
        } ?: toHexString()

    private fun showBuildingImage(buildingType: BuildingType?) {
        if (buildingType == null) {
            binding.imageReadBuilding.visibility = View.GONE
            return
        }

        binding.imageReadBuilding.setImageResource(buildingType.imageResId)
        binding.imageReadBuilding.contentDescription = "Building: ${buildingType.name}"
        binding.imageReadBuilding.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear the binding when the view is destroyed
        Log.d(TAG, "onDestroyView called.")
    }
}
