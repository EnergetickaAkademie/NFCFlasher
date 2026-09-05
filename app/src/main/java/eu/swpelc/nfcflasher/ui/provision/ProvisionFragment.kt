package eu.swpelc.nfcflasher.ui.provision

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import eu.swpelc.nfcflasher.data.ConfigRepository
import eu.swpelc.nfcflasher.databinding.FragmentProvisionBinding
import eu.swpelc.nfcflasher.nfc.EnakNfcProtocol
import eu.swpelc.nfcflasher.nfc.NfcRecordSpec
import eu.swpelc.nfcflasher.viewmodel.SharedViewModel

class ProvisionFragment : Fragment() {

    private var _binding: FragmentProvisionBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var configRepository: ConfigRepository

    private var pendingMessage: NdefMessage? = null
    private var pendingDescription: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProvisionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configRepository = ConfigRepository(requireContext())

        binding.checkBoxOpenNetwork.setOnCheckedChangeListener { _, isChecked ->
            binding.editTextWifiPassword.isEnabled = !isChecked
        }

        binding.buttonPrepareReset.setOnClickListener {
            val mode = configRepository.getProtocolMode()
            val record = EnakNfcProtocol.createResetRecord(mode).toNdefRecord()
            prepareMessage(
                NdefMessage(arrayOf(record)),
                "${mode.displayName} building reset card"
            )
        }

        binding.buttonPrepareDebug.setOnClickListener {
            val record = EnakNfcProtocol.createDebugRecord().toNdefRecord()
            prepareMessage(
                NdefMessage(arrayOf(record)),
                "Protocol v2 debug card"
            )
        }

        binding.buttonPrepareWifi.setOnClickListener {
            createWifiRecord()?.let { record ->
                val mode = configRepository.getProtocolMode()
                prepareMessage(
                    NdefMessage(arrayOf(record)),
                    "${mode.displayName} Wi-Fi provisioning tag"
                )
            }
        }

        sharedViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            if (tag != null && pendingMessage != null) {
                writeNdefMessage(tag, pendingMessage!!, pendingDescription ?: "administrative tag")
            }
        }
    }

    private fun createWifiRecord(): NdefRecord? {
        val ssidText = binding.editTextWifiSsid.text.toString()
        val passwordText = binding.editTextWifiPassword.text.toString()
        val ssid = ssidText.toByteArray(Charsets.UTF_8)
        val isOpen = binding.checkBoxOpenNetwork.isChecked
        val password = if (isOpen) {
            ByteArray(0)
        } else {
            passwordText.toByteArray(Charsets.UTF_8)
        }

        if (ssid.isEmpty() || ssid.size > 32) {
            binding.editTextWifiSsid.error = "SSID must contain 1–32 UTF-8 bytes"
            return null
        }
        if (!isOpen && password.size !in 8..63) {
            binding.editTextWifiPassword.error = "Password must contain 8–63 UTF-8 bytes"
            return null
        }

        val mode = configRepository.getProtocolMode()
        return EnakNfcProtocol.createWifiRecord(ssidText, passwordText, isOpen, mode)
            .toNdefRecord()
    }

    private fun NfcRecordSpec.toNdefRecord() = NdefRecord(
        tnf,
        type,
        ByteArray(0),
        payload
    )

    private fun prepareMessage(message: NdefMessage, description: String) {
        pendingMessage = message
        pendingDescription = description
        binding.textViewProvisionStatus.text = "Ready to write $description. Hold a tag to the phone."
    }

    private fun writeNdefMessage(tag: Tag, message: NdefMessage, description: String) {
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.use {
                    it.connect()
                    require(it.isWritable) { "Tag is read-only" }
                    require(it.maxSize >= message.toByteArray().size) { "Tag storage is too small" }
                    it.writeNdefMessage(message)
                }
            } else {
                val formatable = NdefFormatable.get(tag)
                    ?: throw IllegalArgumentException("Tag does not support NDEF formatting")
                formatable.use {
                    it.connect()
                    it.format(message)
                }
            }

            binding.textViewProvisionStatus.text = "Successfully wrote $description."
            Toast.makeText(context, "Wrote $description", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Wrote $description")
        } catch (exception: Exception) {
            binding.textViewProvisionStatus.text = "Write failed: ${exception.message}"
            Toast.makeText(context, "Write failed: ${exception.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Administrative tag write failed", exception)
        } finally {
            pendingMessage = null
            pendingDescription = null
            sharedViewModel.tagProcessed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ProvisionFragmentNFC"
    }
}
