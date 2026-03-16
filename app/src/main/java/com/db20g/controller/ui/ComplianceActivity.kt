package com.db20g.controller.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.R
import com.db20g.controller.compliance.FccComplianceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ComplianceActivity : AppCompatActivity(), FccComplianceManager.ComplianceListener {

    private lateinit var compliance: FccComplianceManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    // Views
    private lateinit var etCallsign: TextInputEditText
    private lateinit var cardLicense: MaterialCardView
    private lateinit var tvLicenseCallsign: TextView
    private lateinit var tvLicenseName: TextView
    private lateinit var tvLicenseStatus: TextView
    private lateinit var tvLicenseExpiry: TextView
    private lateinit var tvRenewalWarning: TextView
    private lateinit var tvIdTimer: TextView
    private lateinit var tvIdStatus: TextView
    private lateinit var ivPowerChart: ImageView
    private lateinit var rvFamilyMembers: RecyclerView
    private lateinit var rvRules: RecyclerView
    private lateinit var rvViolations: RecyclerView
    private lateinit var tvNoViolations: TextView

    // Adapters
    private lateinit var familyAdapter: FamilyAdapter
    private lateinit var rulesAdapter: RulesAdapter
    private lateinit var violationAdapter: ViolationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeId = getSharedPreferences("app_settings", MODE_PRIVATE).getInt("theme_id", 0)
        when (themeId) {
            1 -> setTheme(R.style.Theme_DB20GController_AMOLED)
            2 -> setTheme(R.style.Theme_DB20GController_RedLight)
            else -> setTheme(R.style.Theme_DB20GController)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compliance)

        compliance = FccComplianceManager(this)
        compliance.setListener(this)
        compliance.loadViolationLog()

        initViews()
        setupLicenseLookup()
        setupCallsignIdTimer()
        setupFamilyMembers()
        setupPowerChart()
        setupPart95Reference()
        setupViolationLog()
        setupAuditButton()

        // Load cached license if available
        compliance.getCachedLicense()?.let { displayLicense(it) }
    }

    private fun initViews() {
        etCallsign = findViewById(R.id.etCallsign)
        cardLicense = findViewById(R.id.cardLicense)
        tvLicenseCallsign = findViewById(R.id.tvLicenseCallsign)
        tvLicenseName = findViewById(R.id.tvLicenseName)
        tvLicenseStatus = findViewById(R.id.tvLicenseStatus)
        tvLicenseExpiry = findViewById(R.id.tvLicenseExpiry)
        tvRenewalWarning = findViewById(R.id.tvRenewalWarning)
        tvIdTimer = findViewById(R.id.tvIdTimer)
        tvIdStatus = findViewById(R.id.tvIdStatus)
        ivPowerChart = findViewById(R.id.ivPowerChart)
        rvFamilyMembers = findViewById(R.id.rvFamilyMembers)
        rvRules = findViewById(R.id.rvRules)
        rvViolations = findViewById(R.id.rvViolations)
        tvNoViolations = findViewById(R.id.tvNoViolations)
    }

    // --- License Lookup ---

    private fun setupLicenseLookup() {
        val btnLookup = findViewById<MaterialButton>(R.id.btnLookup)
        btnLookup.setOnClickListener {
            val callsign = etCallsign.text?.toString()?.trim() ?: return@setOnClickListener
            if (callsign.isEmpty()) {
                Toast.makeText(this, "Enter a callsign", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnLookup.isEnabled = false
            btnLookup.text = "Looking up..."
            scope.launch {
                compliance.lookupLicense(callsign)
                btnLookup.isEnabled = true
                btnLookup.text = "Lookup"
            }
        }
    }

    private fun displayLicense(info: FccComplianceManager.LicenseInfo) {
        cardLicense.visibility = View.VISIBLE
        tvLicenseCallsign.text = info.callsign
        tvLicenseName.text = info.name
        tvLicenseStatus.text = "Status: ${info.status}"
        tvLicenseStatus.setTextColor(
            if (info.status == "Active") Color.parseColor("#4CAF50")
            else Color.parseColor("#FF5252")
        )
        tvLicenseExpiry.text = "Expires: ${info.expireDate}"

        val days = compliance.daysUntilExpiration()
        if (days in 1..90) {
            tvRenewalWarning.visibility = View.VISIBLE
            tvRenewalWarning.text = "⚠ License expires in $days days — file for renewal!"
        } else if (days <= 0 && days != -1) {
            tvRenewalWarning.visibility = View.VISIBLE
            tvRenewalWarning.text = "⛔ LICENSE EXPIRED — do not transmit"
            tvRenewalWarning.setTextColor(Color.parseColor("#FF5252"))
        } else {
            tvRenewalWarning.visibility = View.GONE
        }

        etCallsign.setText(info.callsign)
    }

    // --- Callsign ID Timer ---

    private fun setupCallsignIdTimer() {
        val btnMarkId = findViewById<MaterialButton>(R.id.btnMarkId)
        btnMarkId.setOnClickListener {
            compliance.recordCallsignId()
            Toast.makeText(this, "Callsign ID recorded", Toast.LENGTH_SHORT).show()
        }

        timerRunnable = object : Runnable {
            override fun run() {
                val remaining = compliance.getCallsignIdRemainingSeconds()
                val minutes = remaining / 60
                val seconds = remaining % 60
                tvIdTimer.text = String.format("%02d:%02d", minutes, seconds)

                if (remaining <= 0) {
                    tvIdStatus.text = "⚠ ID is DUE — identify with your callsign!"
                    tvIdStatus.setTextColor(Color.parseColor("#FF5252"))
                    tvIdTimer.setTextColor(Color.parseColor("#FF5252"))
                } else if (remaining < 120) {
                    tvIdStatus.text = "ID due soon"
                    tvIdStatus.setTextColor(Color.parseColor("#FF6D00"))
                    tvIdTimer.setTextColor(Color.parseColor("#FF6D00"))
                } else {
                    tvIdStatus.text = "Time until next required ID"
                    tvIdStatus.setTextColor(Color.parseColor("#888888"))
                    tvIdTimer.setTextColor(Color.WHITE)
                }

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable)
    }

    // --- Family Members ---

    private fun setupFamilyMembers() {
        familyAdapter = FamilyAdapter(compliance.getFamilyMembers().toMutableList()) { name ->
            compliance.removeFamilyMember(name)
            familyAdapter.updateMembers(compliance.getFamilyMembers())
        }
        rvFamilyMembers.layoutManager = LinearLayoutManager(this)
        rvFamilyMembers.adapter = familyAdapter

        findViewById<MaterialButton>(R.id.btnAddFamily).setOnClickListener {
            showAddFamilyDialog()
        }
    }

    private fun showAddFamilyDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val nameInput = EditText(this).apply { hint = "Name" }
        val relInput = EditText(this).apply { hint = "Relationship (spouse, child, etc.)" }
        layout.addView(nameInput)
        layout.addView(relInput)

        AlertDialog.Builder(this)
            .setTitle("Add Family Member")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val rel = relInput.text.toString().trim()
                if (name.isNotEmpty() && rel.isNotEmpty()) {
                    compliance.addFamilyMember(name, rel)
                    familyAdapter.updateMembers(compliance.getFamilyMembers())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Power Limits Chart ---

    private fun setupPowerChart() {
        val channels = compliance.gmrsChannels.filter { !it.repeaterInput }
        val width = 800
        val height = 400
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint().apply { color = Color.parseColor("#1E1E1E") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val margin = 60f
        val chartW = width - margin * 2
        val chartH = height - margin * 2
        val maxPower = 50f

        // Grid lines
        val gridPaint = Paint().apply {
            color = Color.parseColor("#333333")
            strokeWidth = 1f
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#888888")
            textSize = 20f
            isAntiAlias = true
        }

        for (p in listOf(0.5f, 5f, 25f, 50f)) {
            val y = margin + chartH * (1f - p / maxPower)
            canvas.drawLine(margin, y, width - margin, y, gridPaint)
            canvas.drawText("${p}W", 4f, y + 6f, labelPaint)
        }

        // Bars
        val barWidth = chartW / channels.size
        val barPaint = Paint().apply { isAntiAlias = true }

        for ((i, ch) in channels.withIndex()) {
            val x = margin + i * barWidth
            val barH = chartH * (ch.maxPowerWatts.toFloat() / maxPower)
            val y = margin + chartH - barH

            barPaint.color = when {
                ch.maxPowerWatts <= 0.5 -> Color.parseColor("#2962FF")
                ch.maxPowerWatts <= 5.0 -> Color.parseColor("#4CAF50")
                else -> Color.parseColor("#FF6D00")
            }

            canvas.drawRect(x + 2f, y, x + barWidth - 2f, margin + chartH, barPaint)

            // Channel label
            val chLabel = "CH${ch.number}"
            val chPaint = Paint().apply {
                color = Color.WHITE
                textSize = 14f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(chLabel, x + barWidth / 2, height - 8f, chPaint)
        }

        // Title
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("GMRS Power Limits", width / 2f, 30f, titlePaint)

        // Legend
        val legendPaint = Paint().apply { textSize = 16f; isAntiAlias = true }
        val legendY = 50f
        legendPaint.color = Color.parseColor("#2962FF")
        canvas.drawRect(margin, legendY - 12f, margin + 16f, legendY, legendPaint)
        legendPaint.color = Color.parseColor("#AAAAAA")
        canvas.drawText("0.5W FRS", margin + 20f, legendY, legendPaint)

        legendPaint.color = Color.parseColor("#4CAF50")
        canvas.drawRect(margin + 130f, legendY - 12f, margin + 146f, legendY, legendPaint)
        legendPaint.color = Color.parseColor("#AAAAAA")
        canvas.drawText("5W Shared", margin + 150f, legendY, legendPaint)

        legendPaint.color = Color.parseColor("#FF6D00")
        canvas.drawRect(margin + 280f, legendY - 12f, margin + 296f, legendY, legendPaint)
        legendPaint.color = Color.parseColor("#AAAAAA")
        canvas.drawText("50W GMRS", margin + 300f, legendY, legendPaint)

        ivPowerChart.setImageBitmap(bmp)
    }

    // --- Part 95 Reference ---

    private fun setupPart95Reference() {
        rulesAdapter = RulesAdapter(compliance.part95Reference.toMutableList())
        rvRules.layoutManager = LinearLayoutManager(this)
        rvRules.adapter = rulesAdapter

        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupCategory)
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val rules = when {
                checkedIds.contains(R.id.chipGeneral) -> compliance.getRulesByCategory("General")
                checkedIds.contains(R.id.chipLicensing) -> compliance.getRulesByCategory("Licensing")
                checkedIds.contains(R.id.chipOperations) -> compliance.getRulesByCategory("Operations")
                checkedIds.contains(R.id.chipTechnical) -> compliance.getRulesByCategory("Technical")
                checkedIds.contains(R.id.chipRepeaters) -> compliance.getRulesByCategory("Repeaters")
                else -> compliance.part95Reference
            }
            rulesAdapter.updateRules(rules)
        }
    }

    // --- Violation Log ---

    private fun setupViolationLog() {
        violationAdapter = ViolationAdapter(compliance.getViolationLog().toMutableList())
        rvViolations.layoutManager = LinearLayoutManager(this)
        rvViolations.adapter = violationAdapter
        updateViolationVisibility()
    }

    private fun updateViolationVisibility() {
        val violations = compliance.getViolationLog()
        if (violations.isEmpty()) {
            tvNoViolations.visibility = View.VISIBLE
            rvViolations.visibility = View.GONE
        } else {
            tvNoViolations.visibility = View.GONE
            rvViolations.visibility = View.VISIBLE
        }
    }

    // --- Audit ---

    private fun setupAuditButton() {
        findViewById<MaterialButton>(R.id.btnAudit).setOnClickListener {
            val issues = compliance.runComplianceAudit()
            if (issues.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Compliance Audit")
                    .setMessage("✓ All checks passed — you are compliant!")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                val msg = issues.joinToString("\n\n") { "• $it" }
                AlertDialog.Builder(this)
                    .setTitle("Compliance Issues Found")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // --- Listener Callbacks ---

    override fun onLicenseLoaded(license: FccComplianceManager.LicenseInfo) {
        runOnUiThread { displayLicense(license) }
    }

    override fun onLicenseLookupFailed(error: String) {
        runOnUiThread {
            Toast.makeText(this, "License lookup failed: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onViolationDetected(violation: FccComplianceManager.Violation) {
        runOnUiThread {
            violationAdapter.addViolation(violation)
            updateViolationVisibility()
        }
    }

    override fun onComplianceStatusChanged(isCompliant: Boolean) {
        runOnUiThread {
            val color = if (isCompliant) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt()
            supportActionBar?.subtitle = if (isCompliant) "Compliant" else "Compliance Issues Detected"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        scope.cancel()
    }

    // === Adapters ===

    inner class FamilyAdapter(
        private val members: MutableList<FccComplianceManager.FamilyMember>,
        private val onRemove: (String) -> Unit
    ) : RecyclerView.Adapter<FamilyAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvMemberName)
            val tvRel: TextView = view.findViewById(R.id.tvRelationship)
            val btnRemove: ImageView = view.findViewById(R.id.btnRemoveMember)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_family_member, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = members[position]
            holder.tvName.text = m.name
            holder.tvRel.text = m.relationship
            holder.btnRemove.setOnClickListener { onRemove(m.name) }
        }

        override fun getItemCount() = members.size

        fun updateMembers(newMembers: List<FccComplianceManager.FamilyMember>) {
            members.clear()
            members.addAll(newMembers)
            notifyDataSetChanged()
        }
    }

    inner class RulesAdapter(
        private val rules: MutableList<FccComplianceManager.Part95Rule>
    ) : RecyclerView.Adapter<RulesAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvSection: TextView = view.findViewById(R.id.tvSection)
            val tvCategory: TextView = view.findViewById(R.id.tvCategory)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvSummary: TextView = view.findViewById(R.id.tvSummary)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_part95_rule, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = rules[position]
            holder.tvSection.text = "§${r.section}"
            holder.tvCategory.text = r.category
            holder.tvTitle.text = r.title
            holder.tvSummary.text = r.summary
        }

        override fun getItemCount() = rules.size

        fun updateRules(newRules: List<FccComplianceManager.Part95Rule>) {
            rules.clear()
            rules.addAll(newRules)
            notifyDataSetChanged()
        }
    }

    inner class ViolationAdapter(
        private val violations: MutableList<FccComplianceManager.Violation>
    ) : RecyclerView.Adapter<ViolationAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tvViolationType)
            val tvMessage: TextView = view.findViewById(R.id.tvViolationMessage)
            val tvTime: TextView = view.findViewById(R.id.tvViolationTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_violation, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val v = violations[position]
            holder.tvType.text = v.type.name.replace("_", " ")
            holder.tvMessage.text = v.message
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            holder.tvTime.text = sdf.format(java.util.Date(v.timestamp))
        }

        override fun getItemCount() = violations.size

        fun addViolation(v: FccComplianceManager.Violation) {
            violations.add(0, v)
            notifyItemInserted(0)
        }
    }
}
