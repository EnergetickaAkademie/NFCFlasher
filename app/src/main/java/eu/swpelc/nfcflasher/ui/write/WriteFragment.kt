package eu.swpelc.nfcflasher.ui.write

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable // Required for the new writeNfcTag
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
import eu.swpelc.nfcflasher.data.ConfigRepository
import eu.swpelc.nfcflasher.databinding.FragmentWriteBinding
import eu.swpelc.nfcflasher.nfc.EnakNfcProtocol
import eu.swpelc.nfcflasher.viewmodel.SharedViewModel

class WriteFragment : Fragment() {

    private var _binding: FragmentWriteBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var configRepository: ConfigRepository

    private var isContinuousWriteActive = false
    private var buildingTypeForContinuousWrite: BuildingType? = null
    private var valueForContinuousWrite: Byte? = null

    companion object {
        private const val TAG = "WriteFragmentNFC"
        // CUSTOM_MIME_TYPE is no longer used by the new writeNfcTag function
        // private const val CUSTOM_MIME_TYPE = "application/vnd.nfcflasher.buildingid"
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

        configRepository = ConfigRepository(requireContext())
        setupSpinner()
        updateUIState() // Initial UI state

        binding.buttonToggleContinuousWrite.setOnClickListener {
            if (isContinuousWriteActive) {
                isContinuousWriteActive = false
                buildingTypeForContinuousWrite = null
                valueForContinuousWrite = null
                sharedViewModel.setWriteActive(false)
                sharedViewModel.setWriteSelection(null, null)
                // Toast.makeText(context, "Continuous write stopped.", Toast.LENGTH_SHORT).show() // Using updateUIState for status
            } else {
                val selectedItem = binding.spinnerBuildingType.selectedItem
                if (selectedItem == null || selectedItem !is BuildingType) {
                    Toast.makeText(context, "Please select a valid building type first.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                buildingTypeForContinuousWrite = selectedItem as BuildingType
                val selectedValue = configRepository.getCustomValue(buildingTypeForContinuousWrite!!)
                    ?: buildingTypeForContinuousWrite!!.byteValue
                if (selectedValue.toUByte().toInt() !in 0..EnakNfcProtocol.MAX_BUILDING_TYPE) {
                    Toast.makeText(
                        context,
                        "Building value must be between 0 and ${EnakNfcProtocol.MAX_BUILDING_TYPE}.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                valueForContinuousWrite = selectedValue
                isContinuousWriteActive = true
                sharedViewModel.setWriteActive(true)
                sharedViewModel.setWriteSelection(buildingTypeForContinuousWrite!!.name, valueForContinuousWrite)
                // Toast.makeText(context, "Continuous write started for ${buildingTypeForContinuousWrite!!.name}.", Toast.LENGTH_SHORT).show() // Using updateUIState for status
            }
            updateUIState()
        }

        sharedViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            Log.d(TAG, "WriteFragment observer triggered. Tag: ${tag?.toString()}")
            if (tag != null) {
                if (isContinuousWriteActive && buildingTypeForContinuousWrite != null && valueForContinuousWrite != null) {
                    Log.i(TAG, "Continuous write mode: Attempting to write ${buildingTypeForContinuousWrite!!.name} (Value: 0x${valueForContinuousWrite!!.toUByte().toString(16).uppercase()}) to tag.")
                    writeNfcTag(tag, buildingTypeForContinuousWrite!!, valueForContinuousWrite!!)
                } else {
                    Log.d(TAG, "Tag detected but continuous write not active.")
                    Toast.makeText(context, "Please start continuous write mode first.", Toast.LENGTH_SHORT).show()
                    // Clear tag but keep write UI state persisted
                    sharedViewModel.setNfcTag(null)
                }
            } else {
                Log.d(TAG, "NFC Tag became null in WriteFragment.")
            }
        }

        // Restore persisted write UI state
        sharedViewModel.writeActive.observe(viewLifecycleOwner) { active ->
            isContinuousWriteActive = active ?: false
            updateUIState()
        }
        sharedViewModel.writeSelectedTypeName.observe(viewLifecycleOwner) { name ->
            name?.let { typeName ->
                // restore spinner selection
                val adapter = binding.spinnerBuildingType.adapter
                for (i in 0 until (adapter?.count ?: 0)) {
                    val item = adapter?.getItem(i) as? BuildingType
                    if (item?.name == typeName) {
                        binding.spinnerBuildingType.setSelection(i)
                        buildingTypeForContinuousWrite = item
                        break
                    }
                }
            }
        }
        sharedViewModel.writeSelectedValue.observe(viewLifecycleOwner) { value ->
            valueForContinuousWrite = value
        }
    }

    private fun updateUIState() {
        if (isContinuousWriteActive) {
            binding.spinnerBuildingType.isEnabled = false
            binding.buttonToggleContinuousWrite.text = "Stop Continuous Write"
            val typeName = buildingTypeForContinuousWrite?.name ?: "Unknown"
            val hexValue = valueForContinuousWrite?.toUByte()?.toString(16)?.padStart(2, '0')?.uppercase() ?: "XX"
            val protocol = configRepository.getProtocolMode().displayName
            binding.textViewWriteStatus.text = "Writing: $typeName (0x$hexValue) using $protocol\nApproach tags to write."
        } else {
            binding.spinnerBuildingType.isEnabled = true
            binding.buttonToggleContinuousWrite.text = "Start Continuous Write"
            binding.textViewWriteStatus.text = "Select building type and press Start."
        }
    }

    override fun onResume() {
        super.onResume()
        if (::configRepository.isInitialized && !isContinuousWriteActive) {
            setupSpinner()
        }
        updateUIState()
    }

    private inner class BuildingTypeArrayAdapter(
        context: Context,
        textViewResourceId: Int,
        private val buildingTypes: Array<BuildingType>
    ) : ArrayAdapter<BuildingType>(context, textViewResourceId, buildingTypes) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getCustomView(position, convertView, parent, false)
        }
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getCustomView(position, convertView, parent, true)
        }
        private fun getCustomView(position: Int, convertView: View?, parent: ViewGroup, isDropDownView: Boolean): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(
                if (isDropDownView) android.R.layout.simple_spinner_dropdown_item else android.R.layout.simple_spinner_item,
                parent, false
            )
            val textView = view.findViewById<TextView>(android.R.id.text1)
            val buildingType = getItem(position)
            if (buildingType != null && ::configRepository.isInitialized) {
                val customValue = configRepository.getCustomValue(buildingType)
                val effectiveValue = customValue ?: buildingType.byteValue
                val hexValue = effectiveValue.toUByte().toString(16).padStart(2, '0').uppercase()
                val overrideIndicator = if (customValue != null) " (overridden)" else ""
                textView.text = "${buildingType.name} (0x$hexValue$overrideIndicator)"
            } else {
                textView.text = buildingType?.name ?: "Unknown"
            }
            return view
        }
    }

    private fun setupSpinner() {
        val buildingTypes = BuildingType.entries.toTypedArray()
        val adapter = BuildingTypeArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, buildingTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBuildingType.adapter = adapter
        buildingTypeForContinuousWrite?.let { lockedType ->
            val position = adapter.getPosition(lockedType)
            if (position >= 0) {
                binding.spinnerBuildingType.setSelection(position)
            }
        }
    }

    private fun writeNfcTag(tag: Tag, buildingType: BuildingType, valueToWrite: Byte) {
        val protocolMode = configRepository.getProtocolMode()
        val recordSpec = EnakNfcProtocol.createBuildingRecord(valueToWrite, protocolMode)
        val record = NdefRecord(
            recordSpec.tnf,
            recordSpec.type,
            ByteArray(0),
            recordSpec.payload
        )
        val msg = NdefMessage(arrayOf(record))

        var processed = false
        try {
            // Try NDEF first
            Ndef.get(tag)?.use { ndef ->
                ndef.connect()
                if (!ndef.isWritable) {
                    Toast.makeText(context, "Tag is not writable.", Toast.LENGTH_LONG).show()
                    return // Exits writeNfcTag, finally will run
                }
                if (ndef.maxSize < msg.toByteArray().size) {
                    Toast.makeText(context, "Tag storage too small.", Toast.LENGTH_LONG).show()
                    return // Exits writeNfcTag, finally will run
                }
                ndef.writeNdefMessage(msg)
                processed = true
            } ?: run {
                // If not already NDEF, try to format
                val fmt = NdefFormatable.get(tag) // Corrected: android.nfc.tech.NdefFormatable
                if (fmt != null) {
                    fmt.use { ndefFormatable -> // Use 'use' for auto-close
                        ndefFormatable.connect()
                        ndefFormatable.format(msg) // formats and writes TLV + message + FE
                        // fmt.close() // Not needed due to 'use'
                    }
                    processed = true
                } else {
                    Toast.makeText(context, "Tag doesn’t support NDEF or NDEF Format.", Toast.LENGTH_LONG).show()
                }
            }

            if (processed) {
                val hex = valueToWrite.toUByte().toString(16).padStart(2, '0').uppercase()
                Toast.makeText(
                    context,
                    "Wrote ${buildingType.name} (0x$hex) using ${protocolMode.displayName}",
                    Toast.LENGTH_LONG
                ).show()
                Log.i(TAG, "Wrote ${buildingType.name} payload=0x$hex protocol=${protocolMode.name}")
            }
        } catch (e: Exception) { // Catch more specific exceptions if possible (IOException, FormatException)
            Log.e(TAG, "Write failed", e)
            Toast.makeText(context, "Write failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            sharedViewModel.tagProcessed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

