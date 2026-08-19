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
import eu.swpelc.nfcflasher.databinding.FragmentProvisionBinding
import eu.swpelc.nfcflasher.viewmodel.SharedViewModel
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class ProvisionFragment : Fragment() {

    private var _binding: FragmentProvisionBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: SharedViewModel by activityViewModels()

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

        binding.checkBoxOpenNetwork.setOnCheckedChangeListener { _, isChecked ->
            binding.editTextWifiPassword.isEnabled = !isChecked
        }

        binding.buttonPrepareReset.setOnClickListener {
            val record = externalRecord(COMMAND_RECORD_TYPE, byteArrayOf(VERSION, RESET_BUILDINGS))
            prepareMessage(NdefMessage(arrayOf(record)), "building reset card")
        }

        binding.buttonPrepareWifi.setOnClickListener {
            createWifiRecord()?.let { record ->
                prepareMessage(NdefMessage(arrayOf(record)), "Wi-Fi provisioning tag")
            }
        }

        sharedViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
            if (tag != null && pendingMessage != null) {
                writeNdefMessage(tag, pendingMessage!!, pendingDescription ?: "administrative tag")
            }
        }
    }

    private fun createWifiRecord(): NdefRecord? {
        val ssid = binding.editTextWifiSsid.text.toString().toByteArray(Charsets.UTF_8)
        val isOpen = binding.checkBoxOpenNetwork.isChecked
        val password = if (isOpen) {
            ByteArray(0)
        } else {
            binding.editTextWifiPassword.text.toString().toByteArray(Charsets.UTF_8)
        }

        if (ssid.isEmpty() || ssid.size > 32) {
            binding.editTextWifiSsid.error = "SSID must contain 1–32 UTF-8 bytes"
            return null
        }
        if (!isOpen && password.size !in 8..63) {
            binding.editTextWifiPassword.error = "Password must contain 8–63 UTF-8 bytes"
            return null
        }

        val body = ByteArrayOutputStream().apply {
            write(VERSION.toInt())
            write(if (isOpen) SECURITY_OPEN else SECURITY_WPA)
            write(ssid.size)
            write(ssid)
            write(password.size)
            write(password)
        }.toByteArray()

        val checksum = CRC32().apply { update(body) }.value
        val payload = ByteArrayOutputStream().apply {
            write(body)
            write((checksum ushr 24).toInt() and 0xff)
            write((checksum ushr 16).toInt() and 0xff)
            write((checksum ushr 8).toInt() and 0xff)
            write(checksum.toInt() and 0xff)
        }.toByteArray()

        return externalRecord(WIFI_RECORD_TYPE, payload)
    }

    private fun externalRecord(type: String, payload: ByteArray) = NdefRecord(
        NdefRecord.TNF_EXTERNAL_TYPE,
        type.toByteArray(Charsets.US_ASCII),
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
        private const val COMMAND_RECORD_TYPE = "cz.enak:cmd"
        private const val WIFI_RECORD_TYPE = "cz.enak:wifi"
        private const val VERSION: Byte = 1
        private const val RESET_BUILDINGS: Byte = 1
        private const val SECURITY_OPEN = 0
        private const val SECURITY_WPA = 1
    }
}
