package com.db20g.controller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.db20g.controller.databinding.FragmentSettingsBinding
import com.db20g.controller.protocol.RadioSettings
import com.db20g.controller.protocol.ScanMode

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()
    private lateinit var themeManager: ThemeManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        themeManager = ThemeManager(requireContext())
        setupThemeSelector()

        // Squelch slider
        binding.sliderSquelch.addOnChangeListener { _, value, fromUser ->
            binding.tvSquelchValue.text = value.toInt().toString()
            if (fromUser) applySettings()
        }

        // VOX slider
        binding.sliderVox.addOnChangeListener { _, value, fromUser ->
            binding.tvVoxValue.text = if (value.toInt() == 0) "Off" else value.toInt().toString()
            if (fromUser) applySettings()
        }

        // Backlight slider
        binding.sliderBacklight.addOnChangeListener { _, value, fromUser ->
            binding.tvBacklightValue.text = value.toInt().toString()
            if (fromUser) applySettings()
        }

        // TOT slider
        binding.sliderTot.addOnChangeListener { _, value, fromUser ->
            val seconds = value.toInt() * 30
            binding.tvTotValue.text = if (seconds == 0) "Off" else "${seconds}s"
            if (fromUser) applySettings()
        }

        // Toggle switches
        binding.switchBeep.setOnCheckedChangeListener { _, _ -> applySettings() }
        binding.switchRoger.setOnCheckedChangeListener { _, _ -> applySettings() }
        binding.switchAutoLock.setOnCheckedChangeListener { _, _ -> applySettings() }
        binding.rgScanMode.setOnCheckedChangeListener { _, _ -> applySettings() }

        // Observe settings
        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings != null) {
                populateSettings(settings)
            }
        }

        viewModel.radioIdent.observe(viewLifecycleOwner) { ident ->
            if (ident.isNotEmpty()) {
                binding.tvRadioIdent.text = "Ident: $ident"
                binding.tvRadioModel.text = "Radioddity DB20-G"
            }
        }
    }

    private fun setupThemeSelector() {
        binding.tvCurrentTheme.text = "Current: ${themeManager.getThemeDisplayName()}"

        when (themeManager.currentTheme) {
            ThemeManager.THEME_DEFAULT -> binding.rbThemeDefault.isChecked = true
            ThemeManager.THEME_AMOLED -> binding.rbThemeAmoled.isChecked = true
            ThemeManager.THEME_RED_LIGHT -> binding.rbThemeRedLight.isChecked = true
            ThemeManager.THEME_SYSTEM -> binding.rbThemeSystem.isChecked = true
        }

        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                binding.rbThemeAmoled.id -> ThemeManager.THEME_AMOLED
                binding.rbThemeRedLight.id -> ThemeManager.THEME_RED_LIGHT
                binding.rbThemeSystem.id -> ThemeManager.THEME_SYSTEM
                else -> ThemeManager.THEME_DEFAULT
            }
            if (newTheme != themeManager.currentTheme) {
                themeManager.currentTheme = newTheme
                themeManager.applyNightMode()
                requireActivity().recreate()
            }
        }
    }

    private fun populateSettings(settings: RadioSettings) {
        binding.sliderSquelch.value = settings.squelch.toFloat().coerceIn(0f, 9f)
        binding.tvSquelchValue.text = settings.squelch.toString()

        binding.sliderVox.value = settings.vox.toFloat().coerceIn(0f, 10f)
        binding.tvVoxValue.text = if (settings.vox == 0) "Off" else settings.vox.toString()

        binding.sliderBacklight.value = settings.backlight.toFloat().coerceIn(0f, 9f)
        binding.tvBacklightValue.text = settings.backlight.toString()

        binding.sliderTot.value = settings.timeoutTimer.toFloat().coerceIn(0f, 10f)
        val totSeconds = settings.timeoutTimer * 30
        binding.tvTotValue.text = if (totSeconds == 0) "Off" else "${totSeconds}s"

        binding.switchBeep.isChecked = settings.beep
        binding.switchRoger.isChecked = settings.roger
        binding.switchAutoLock.isChecked = settings.autoLock

        when (settings.scanMode) {
            ScanMode.TIME_OPERATED -> binding.rbScanTime.isChecked = true
            ScanMode.CARRIER_OPERATED -> binding.rbScanCarrier.isChecked = true
            ScanMode.SEARCH -> binding.rbScanSearch.isChecked = true
        }
    }

    private fun applySettings() {
        collectSettings()?.let { viewModel.updateSettings(it) }
    }

    fun collectSettings(): RadioSettings? {
        val current = viewModel.settings.value ?: return null
        return current.copy(
            squelch = binding.sliderSquelch.value.toInt(),
            vox = binding.sliderVox.value.toInt(),
            backlight = binding.sliderBacklight.value.toInt(),
            timeoutTimer = binding.sliderTot.value.toInt(),
            beep = binding.switchBeep.isChecked,
            roger = binding.switchRoger.isChecked,
            autoLock = binding.switchAutoLock.isChecked,
            scanMode = when (binding.rgScanMode.checkedRadioButtonId) {
                binding.rbScanCarrier.id -> ScanMode.CARRIER_OPERATED
                binding.rbScanSearch.id -> ScanMode.SEARCH
                else -> ScanMode.TIME_OPERATED
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
