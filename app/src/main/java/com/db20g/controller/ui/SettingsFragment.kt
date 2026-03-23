package com.db20g.controller.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.db20g.controller.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSettingsNavigation()

        viewModel.radioIdent.observe(viewLifecycleOwner) { ident ->
            if (ident.isNotEmpty()) {
                binding.tvRadioIdent.text = "Ident: $ident"
                binding.tvRadioModel.text = "Radioddity DB20-G"
            }
        }
    }

    private fun setupSettingsNavigation() {
        binding.cardRadioSettings.setOnClickListener {
            startActivity(Intent(requireContext(), RadioSettingsActivity::class.java))
        }
        binding.cardAppearance.setOnClickListener {
            startActivity(Intent(requireContext(), AppearanceActivity::class.java))
        }
        binding.cardComplianceSettings.setOnClickListener {
            startActivity(Intent(requireContext(), ComplianceActivity::class.java))
        }
        binding.cardBluetoothSettings.setOnClickListener {
            startActivity(Intent(requireContext(), BluetoothPttActivity::class.java))
        }
        binding.cardRecordingSettings.setOnClickListener {
            startActivity(Intent(requireContext(), ToolsActivity::class.java))
        }
        binding.cardDataTools.setOnClickListener {
            startActivity(Intent(requireContext(), DataToolsActivity::class.java))
        }
        binding.cardAccessibilitySettings.setOnClickListener {
            startActivity(Intent(requireContext(), AccessibilitySettingsActivity::class.java))
        }
        binding.cardTranslationSettings.setOnClickListener {
            startActivity(Intent(requireContext(), TranslationActivity::class.java))
        }
        binding.cardHardwarePinout.setOnClickListener {
            startActivity(Intent(requireContext(), PinoutConfigActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
