package eu.swpelc.nfcflasher.ui.write

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.FormatException
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import eu.swpelc.nfcflasher.BuildingType
import eu.swpelc.nfcflasher.R
import eu.swpelc.nfcflasher.data.ConfigRepository // Added import
import eu.swpelc.nfcflasher.databinding.FragmentWriteBinding
import eu.swpelc.nfcflasher.viewmodel.SharedViewModel
import java.io.IOException
import java.nio.charset.Charset

class WriteFragment : Fragment() {

    private var _binding: FragmentWriteBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var currentTag: Tag? = null
    private lateinit var configRepository: ConfigRepository // Added

    companion object {
        private const val TAG = "WriteFragmentNFC"
        // Custom MIME type for our NDEF records
        private const val CUSTOM_MIME_TYPE = "application/vnd.nfcflasher.buildingid"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configRepository = ConfigRepository(requireContext()) // Initialized
        setupSpinner() // Will be called after repository is initialized

        sharedViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            currentTag = tag
            if (tag != null) {
                // Optionally, give feedback that a tag is ready for writing
                // Toast.makeText(context, "Tag detected. Ready to write.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "NFC Tag observed in WriteFragment: $tag")
            } else {
                Log.d(TAG, "NFC Tag became null in WriteFragment.")
            }
        }

        binding.buttonWriteTag.setOnClickListener {
            if (currentTag == null) {
                Toast.makeText(context, "Please approach an NFC tag first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val selectedItem = binding.spinnerBuildingType.selectedItem
            if (selectedItem == null || selectedItem !is BuildingType) { // Check type before cast
                Toast.makeText(context, "Please select a valid building type.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedBuildingType = selectedItem as BuildingType

            // Fetch the effective value right before writing
            val effectiveValue = configRepository.getCustomValue(selectedBuildingType) ?: selectedBuildingType.byteValue
            writeNfcTag(currentTag!!, selectedBuildingType, effectiveValue)
            // We call tagProcessed in writeNfcTag after the attempt, regardless of success/failure for this tag instance
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh spinner when fragment resumes, in case config changed
        if (::configRepository.isInitialized) { // Ensure repository is ready
            setupSpinner()
        }
    }

    // Custom ArrayAdapter for BuildingType to display effective values
    private inner class BuildingTypeArrayAdapter(
        context: Context,
        textViewResourceId: Int,
        private val buildingTypes: Array<BuildingType>
    ) : ArrayAdapter<BuildingType>(context, textViewResourceId, buildingTypes) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // This is for the selected item view in the spinner (when closed)
            return getCustomView(position, convertView, parent, false)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            // This is for the items in the dropdown list
            return getCustomView(position, convertView, parent, true)
        }

        private fun getCustomView(position: Int, convertView: View?, parent: ViewGroup, isDropDownView: Boolean): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(
                // Use different layouts for the spinner item itself vs the dropdown items if needed,
                // for simplicity, we use simple_spinner_item for closed state and simple_spinner_dropdown_item for dropdown
                if (isDropDownView) android.R.layout.simple_spinner_dropdown_item else android.R.layout.simple_spinner_item,
                parent,
                false
            )
            val textView = view.findViewById<TextView>(android.R.id.text1)
            val buildingType = getItem(position)

            if (buildingType != null && ::configRepository.isInitialized) { // Check if repository is initialized
                val customValue = configRepository.getCustomValue(buildingType)
                val effectiveValue = customValue ?: buildingType.byteValue
                // Ensure byte is treated as unsigned for hex conversion if it's > 127
                val hexValue = effectiveValue.toUByte().toString(16).padStart(2, '0').uppercase()
                val overrideIndicator = if (customValue != null) " (overridden)" else "" // Added space
                textView.text = "${buildingType.name} (0x$hexValue$overrideIndicator)"
            } else {
                textView.text = buildingType?.name ?: "Unknown"
            }
            return view
        }
    }

    private fun setupSpinner() {
        val buildingTypes = BuildingType.entries.toTypedArray()
        // Use the custom adapter
        val adapter = BuildingTypeArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item, // Layout for the selected item in the spinner
            buildingTypes
        )
        // Layout for dropdown items
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBuildingType.adapter = adapter
    }

    private fun writeNfcTag(tag: Tag, buildingType: BuildingType, valueToWrite: Byte) {
        val type = "B".toByteArray(Charsets.US_ASCII)      // record type = 'B'
        val payload = byteArrayOf(valueToWrite)            // 1-byte payload

        val record = NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,  // TNF not validated by your reader; SR will be set automatically
            type,
            ByteArray(0),
            payload
        )
        val msg = NdefMessage(arrayOf(record))

        var processed = false
        try {
            // Try NDEF first
            Ndef.get(tag)?.use { ndef ->
                ndef.connect()
                if (!ndef.isWritable) {
                    Toast.makeText(context, "Tag is not writable.", Toast.LENGTH_LONG).show()
                    return
                }
                if (ndef.maxSize < msg.toByteArray().size) {
                    Toast.makeText(context, "Tag storage too small.", Toast.LENGTH_LONG).show()
                    return
                }
                ndef.writeNdefMessage(msg)
                processed = true
            } ?: run {
                // If not already NDEF, try to format
                val fmt = android.nfc.tech.NdefFormatable.get(tag)
                if (fmt != null) {
                    fmt.connect()
                    fmt.format(msg) // formats and writes TLV + message + FE
                    fmt.close()
                    processed = true
                } else {
                    Toast.makeText(context, "Tag doesn’t support NDEF.", Toast.LENGTH_LONG).show()
                }
            }

            if (processed) {
                val hex = valueToWrite.toUByte().toString(16).padStart(2, '0').uppercase()
                Toast.makeText(context, "Wrote ${buildingType.name} (0x$hex)", Toast.LENGTH_LONG).show()
                Log.i(TAG, "Wrote type=B payload=0x$hex")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Write failed", e)
            Toast.makeText(context, "Write failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            sharedViewModel.tagProcessed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentTag = null // Clear reference to the tag
    }
}
