package eu.swpelc.nfcflasher.ui.config

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import eu.swpelc.nfcflasher.databinding.FragmentConfigBinding // Assuming ViewBinding is enabled

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView.

    private lateinit var viewModel: ConfigViewModel
    private lateinit var configAdapter: ConfigAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this).get(ConfigViewModel::class.java)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        configAdapter = ConfigAdapter { buildingConfigDisplayItem ->
            showEditDialog(buildingConfigDisplayItem)
        }
        binding.recyclerViewConfig.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = configAdapter
        }
    }

    private fun setupObservers() {
        viewModel.configItems.observe(viewLifecycleOwner) { items ->
            items?.let {
                configAdapter.submitList(it)
            }
        }
    }

    private fun setupListeners() {
        binding.buttonResetAll.setOnClickListener {
            // Add a confirmation dialog before resetting all
            AlertDialog.Builder(requireContext())
                .setTitle("Reset All Configurations")
                .setMessage("Are you sure you want to reset all building values to their defaults? This cannot be undone.")
                .setPositiveButton("Reset All") { _, _ ->
                    viewModel.resetAllToDefaults()
                    //Toast.makeText(context, "All configurations reset to defaults", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showEditDialog(item: BuildingConfigDisplayItem) {
        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(item.effectiveValue.toString())
            hint = "Value (0-255)"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Value for ${item.typeName}")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newValueString = editText.text.toString()
                try {
                    val newValueInt = newValueString.toInt()
                    if (newValueInt in 0..255) {
                        viewModel.updateBuildingValue(item.typeName, newValueInt.toByte())
                        //Toast.makeText(context, "${item.typeName} updated to $newValueInt", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Value must be between 0 and 255", Toast.LENGTH_LONG).show()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "Invalid number format", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset Item") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Reset ${item.typeName}?")
                    .setMessage("Reset ${item.typeName} to its default value (${item.defaultValue})?")
                    .setPositiveButton("Reset") { _, _ ->
                        viewModel.resetBuildingValue(item.typeName)
                        //Toast.makeText(context, "${item.typeName} reset to default", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important to avoid memory leaks
    }
}
