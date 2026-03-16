package com.db20g.controller.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.R
import com.db20g.controller.databinding.ActivitySstvBinding
import com.db20g.controller.protocol.SstvCodec
import com.db20g.controller.protocol.SstvCodec.SstvMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class SstvActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySstvBinding
    private lateinit var codec: SstvCodec
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var currentBitmap: Bitmap? = null
    private var selectedMode: SstvMode = SstvMode.ROBOT36
    private var isReceiving = false
    private var isTransmitting = false

    private val gallery = mutableListOf<SstvCodec.SstvImage>()
    private lateinit var galleryAdapter: GalleryAdapter

    // Camera capture
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            setPreviewImage(bitmap)
        }
    }

    // Image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadImageFromUri(it) }
    }

    // Permission request
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted) {
            cameraLauncher.launch(null)
        }
        if (!audioGranted) {
            Toast.makeText(this, "Audio permission required for SSTV receive", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme
        val themeId = getSharedPreferences("app_settings", MODE_PRIVATE).getInt("theme_id", 0)
        when (themeId) {
            1 -> setTheme(R.style.Theme_DB20GController_AMOLED)
            2 -> setTheme(R.style.Theme_DB20GController_RedLight)
        }

        super.onCreate(savedInstanceState)
        binding = ActivitySstvBinding.inflate(layoutInflater)
        setContentView(binding.root)

        codec = SstvCodec(this)

        setupToolbar()
        setupModeChips()
        setupControls()
        setupGallery()
        loadGallery()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupModeChips() {
        binding.chipRobot36.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedMode = SstvMode.ROBOT36
                binding.toolbar.subtitle = "Robot 36 — 320×240 — 36 sec"
            }
        }
        binding.chipMartinM1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedMode = SstvMode.MARTIN_M1
                binding.toolbar.subtitle = "Martin M1 — 320×256 — 114 sec"
            }
        }
    }

    private fun setupControls() {
        binding.btnCapture.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                cameraLauncher.launch(null)
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }

        binding.btnPickImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnTransmit.setOnClickListener {
            if (!isTransmitting && currentBitmap != null) {
                startTransmit()
            }
        }

        binding.btnReceive.setOnClickListener {
            if (isReceiving) {
                stopReceive()
            } else {
                startReceive()
            }
        }
    }

    private fun setupGallery() {
        galleryAdapter = GalleryAdapter(gallery) { image ->
            // Tap on gallery thumbnail to show full image
            val bitmap = BitmapFactory.decodeFile(image.file.absolutePath)
            if (bitmap != null) {
                setPreviewImage(bitmap)
                Toast.makeText(this, "${image.mode.label} — ${image.formattedTime()}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvGallery.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvGallery.adapter = galleryAdapter
    }

    private fun loadGallery() {
        gallery.clear()
        gallery.addAll(codec.getGallery())
        galleryAdapter.notifyDataSetChanged()

        binding.tvGalleryCount.text = "${gallery.size} images"
        binding.tvNoGallery.visibility = if (gallery.isEmpty()) View.VISIBLE else View.GONE
        binding.rvGallery.visibility = if (gallery.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun setPreviewImage(bitmap: Bitmap) {
        currentBitmap = bitmap
        binding.ivPreview.setImageBitmap(bitmap)
        binding.tvNoImage.visibility = View.GONE
        binding.btnTransmit.isEnabled = true
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val stream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bitmap != null) {
                setPreviewImage(bitmap)
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTransmit() {
        val bitmap = currentBitmap ?: return
        isTransmitting = true

        binding.btnTransmit.isEnabled = false
        binding.btnCapture.isEnabled = false
        binding.btnPickImage.isEnabled = false
        binding.btnReceive.isEnabled = false
        binding.progressOverlay.visibility = View.VISIBLE
        binding.tvProgress.text = "Transmitting ${selectedMode.label}..."
        binding.tvStatus.text = "TX"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    codec.transmit(bitmap, selectedMode)
                }
                Toast.makeText(this@SstvActivity, "Transmission complete", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SstvActivity, "TX Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isTransmitting = false
                binding.btnTransmit.isEnabled = true
                binding.btnCapture.isEnabled = true
                binding.btnPickImage.isEnabled = true
                binding.btnReceive.isEnabled = true
                binding.progressOverlay.visibility = View.GONE
                binding.tvStatus.text = "Idle"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(this@SstvActivity, android.R.color.darker_gray))
            }
        }
    }

    private fun startReceive() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }

        isReceiving = true
        binding.btnReceive.text = "STOP"
        binding.btnTransmit.isEnabled = false
        binding.tvStatus.text = "RX — Listening..."
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        binding.progressOverlay.visibility = View.VISIBLE
        binding.tvProgress.text = "Listening for SSTV signal..."
        binding.progressBar.isIndeterminate = true

        codec.setReceiveCallback { bitmap, mode ->
            runOnUiThread {
                setPreviewImage(bitmap)
                loadGallery()
                Toast.makeText(this, "Received ${mode.label} image!", Toast.LENGTH_SHORT).show()
            }
        }

        codec.startReceiving()
    }

    private fun stopReceive() {
        isReceiving = false
        codec.stopReceiving()

        binding.btnReceive.text = "RECEIVE"
        binding.btnTransmit.isEnabled = currentBitmap != null
        binding.tvStatus.text = "Idle"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        binding.progressOverlay.visibility = View.GONE
        binding.progressBar.isIndeterminate = false
    }

    override fun onDestroy() {
        super.onDestroy()
        codec.release()
        scope.cancel()
    }

    // ======================== Gallery Adapter ========================

    class GalleryAdapter(
        private val items: List<SstvCodec.SstvImage>,
        private val onClick: (SstvCodec.SstvImage) -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
            val tvMode: TextView = view.findViewById(R.id.tvThumbnailMode)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sstv_thumbnail, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvMode.text = item.mode.label

            if (item.thumbnail != null) {
                holder.ivThumbnail.setImageBitmap(item.thumbnail)
            } else {
                holder.ivThumbnail.setImageBitmap(null)
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
