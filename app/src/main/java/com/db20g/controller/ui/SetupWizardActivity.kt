package com.db20g.controller.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.db20g.controller.R
import com.db20g.controller.databinding.ActivitySetupWizardBinding
import com.db20g.controller.protocol.ChannelTemplates
import com.db20g.controller.protocol.DB20GProtocol
import com.db20g.controller.repeater.CallsignManager
import com.db20g.controller.serial.UsbSerialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupWizardBinding
    private var currentStep = 0
    private val totalSteps = 4

    private val stepViews by lazy {
        listOf(
            binding.stepWelcome,
            binding.stepCallsign,
            binding.stepLocation,
            binding.stepTemplate,
            binding.stepConnect,
        )
    }

    private val stepTitles = listOf(
        "Welcome",
        "Your Callsign",
        "Location Access",
        "Choose a Template",
        "Program Your Radio"
    )

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            binding.tvLocationStatus.text = "Location access granted!"
            binding.btnGrantLocation.isEnabled = false
            binding.btnGrantLocation.text = "Granted"
        } else {
            binding.tvLocationStatus.text = "Location denied. You can enable it later in Settings."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)
        binding = ActivitySetupWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupTemplateSelection()
        setupLocationStep()
        setupProgramStep()
        showStep(0)
    }

    private fun setupNavigation() {
        binding.btnNext.setOnClickListener {
            if (currentStep < totalSteps) {
                if (validateStep(currentStep)) {
                    showStep(currentStep + 1)
                }
            }
        }
        binding.btnBack.setOnClickListener {
            if (currentStep > 0) {
                showStep(currentStep - 1)
            }
        }
    }

    private fun setupLocationStep() {
        binding.btnGrantLocation.setOnClickListener {
            val fineGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fineGranted) {
                binding.tvLocationStatus.text = "Location access already granted!"
                binding.btnGrantLocation.isEnabled = false
            } else {
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }

        // Check if already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            binding.tvLocationStatus.text = "Location access already granted!"
            binding.btnGrantLocation.isEnabled = false
            binding.btnGrantLocation.text = "Granted"
        }
    }

    private fun setupTemplateSelection() {
        binding.rgTemplates.setOnCheckedChangeListener { _, checkedId ->
            val detail = when (checkedId) {
                R.id.rbGmrsStandard -> "22 channels · All frequencies from 462.5625 to 462.7250 MHz · Power limits enforced per FCC rules"
                R.id.rbEmergencyKit -> "30 channels · Emergency Ch 20 in slot 1 · FRS interop channels 1-7 · Repeater pairs 15R-22R"
                R.id.rbFamilyPack -> "8 channels · FRS channels 1-7 at low power (works with FRS handhelds) · Emergency Ch 20"
                R.id.rbFullRepeaters -> "30 channels · All 22 simplex + 8 repeater pairs (15R-22R) with +5 MHz offset"
                R.id.rbSkipTemplate -> "No channels will be pre-loaded. You can program channels manually from the Channels tab."
                else -> ""
            }
            binding.tvTemplateDetail.text = detail
        }
        // Trigger initial detail text
        binding.rgTemplates.check(R.id.rbGmrsStandard)
    }

    private fun setupProgramStep() {
        binding.btnProgram.setOnClickListener {
            programRadio()
        }
        binding.btnSkipProgram.setOnClickListener {
            finishWizard()
        }
    }

    private fun validateStep(step: Int): Boolean {
        return when (step) {
            1 -> {
                val callsign = binding.etCallsign.text.toString().trim().uppercase()
                if (callsign.isNotEmpty() && !callsign.matches(Regex("^W[A-Z]{1,2}[A-Z]?\\d{2,4}$"))) {
                    Toast.makeText(this, "Callsign format looks unusual. Expected WRXX123 format.", Toast.LENGTH_SHORT).show()
                    // Don't block, just warn — user might have a valid unusual callsign
                }
                true
            }
            else -> true
        }
    }

    private fun showStep(step: Int) {
        currentStep = step.coerceIn(0, totalSteps)

        // Update progress
        binding.wizardProgress.progress = currentStep
        binding.tvStepTitle.text = stepTitles[currentStep]

        // Show/hide step views
        stepViews.forEachIndexed { index, view ->
            view.visibility = if (index == currentStep) View.VISIBLE else View.GONE
        }

        // Update navigation buttons
        binding.btnBack.visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        when (currentStep) {
            0 -> binding.btnNext.text = "Get Started"
            totalSteps -> {
                binding.btnNext.text = "Finish"
                binding.btnNext.setOnClickListener { finishWizard() }
                updateSummary()
            }
            else -> binding.btnNext.text = "Next"
        }
    }

    private fun updateSummary() {
        val callsign = binding.etCallsign.text.toString().trim().uppercase().ifEmpty { "(not set)" }
        val templateName = when (binding.rgTemplates.checkedRadioButtonId) {
            R.id.rbGmrsStandard -> "GMRS Standard 22"
            R.id.rbEmergencyKit -> "Emergency Kit"
            R.id.rbFamilyPack -> "Family Pack"
            R.id.rbFullRepeaters -> "Full GMRS + Repeaters"
            R.id.rbSkipTemplate -> "None (manual)"
            else -> "None"
        }
        val locationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        binding.tvSetupSummary.text = buildString {
            append("Callsign: $callsign\n")
            append("Template: $templateName\n")
            append("Location: ${if (locationGranted) "Enabled" else "Disabled"}\n")
            append("Auto-ID: ${if (binding.switchAutoId.isChecked) "On (15 min)" else "Off"}")
        }
    }

    private fun getSelectedTemplateId(): String? {
        return when (binding.rgTemplates.checkedRadioButtonId) {
            R.id.rbGmrsStandard -> "gmrs_standard_22"
            R.id.rbEmergencyKit -> "emergency_kit"
            R.id.rbFamilyPack -> "family_pack"
            R.id.rbFullRepeaters -> "full_with_repeaters"
            else -> null
        }
    }

    private fun programRadio() {
        val templateId = getSelectedTemplateId()
        if (templateId == null) {
            Toast.makeText(this, "No template selected. Skipping programming.", Toast.LENGTH_SHORT).show()
            finishWizard()
            return
        }

        val template = ChannelTemplates.getTemplate(templateId)
        if (template == null) {
            Toast.makeText(this, "Template not found", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnProgram.isEnabled = false
        binding.progressProgram.visibility = View.VISIBLE
        binding.tvProgramStatus.text = "Looking for radio..."

        lifecycleScope.launch {
            try {
                val serialManager = UsbSerialManager(this@SetupWizardActivity)
                val devices = serialManager.findDevices()

                if (devices.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.tvProgramStatus.text = "No radio found. Connect the programming cable and try again."
                        binding.btnProgram.isEnabled = true
                        binding.progressProgram.visibility = View.GONE
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    binding.tvProgramStatus.text = "Connecting to radio..."
                }

                serialManager.connect(devices[0])
                try {
                    val protocol = DB20GProtocol(serialManager)

                    withContext(Dispatchers.Main) {
                        binding.tvProgramStatus.text = "Reading current radio memory..."
                        binding.progressProgram.isIndeterminate = false
                        binding.progressProgram.max = 100
                    }

                    // Download current memory first
                    protocol.download { current, total ->
                        val pct = (current * 100) / total
                        binding.progressProgram.progress = pct
                    }

                    val channels = template.builder()

                    withContext(Dispatchers.Main) {
                        binding.tvProgramStatus.text = "Programming ${channels.size} channels..."
                        binding.progressProgram.progress = 0
                    }

                    // Encode and upload
                    val memImage = protocol.getMemoryImage()
                        ?: throw IllegalStateException("Memory download failed")
                    val encoded = protocol.encodeChannels(channels, memImage)

                    protocol.upload(encoded) { current, total ->
                        val pct = (current * 100) / total
                        binding.progressProgram.progress = pct
                    }

                    withContext(Dispatchers.Main) {
                        binding.progressProgram.visibility = View.GONE
                        binding.tvProgramStatus.text = "Programming complete! ${channels.size} channels loaded."
                        binding.btnProgram.text = "Done!"
                        binding.btnProgram.isEnabled = false
                    }
                } finally {
                    try { serialManager.disconnect() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressProgram.visibility = View.GONE
                    binding.tvProgramStatus.text = "Programming failed: ${e.message}\nYou can try again or program later from the Tools tab."
                    binding.btnProgram.isEnabled = true
                }
            }
        }
    }

    private fun finishWizard() {
        // Save callsign
        val callsign = binding.etCallsign.text.toString().trim().uppercase()
        if (callsign.isNotEmpty()) {
            val callsignManager = CallsignManager(this)
            callsignManager.callsign = callsign
            callsignManager.autoIdEnabled = binding.switchAutoId.isChecked
        }

        // Mark wizard as completed
        getSharedPreferences("db20g_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("setup_wizard_completed", true)
            .apply()

        // Go to main app
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        fun isSetupComplete(activity: AppCompatActivity): Boolean {
            return activity.getSharedPreferences("db20g_prefs", MODE_PRIVATE)
                .getBoolean("setup_wizard_completed", false)
        }
    }
}
