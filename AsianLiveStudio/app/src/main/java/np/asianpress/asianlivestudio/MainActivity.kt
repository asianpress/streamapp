package np.asianpress.asianlivestudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import android.net.Uri
import android.content.Intent
import android.graphics.Color
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.base.recording.RecordController
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.view.OpenGlView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), ConnectChecker {
    private lateinit var openGlView: OpenGlView
    private lateinit var watermarkLayer: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var liveButton: Button
    private lateinit var recordButton: Button
    private lateinit var profileLabel: TextView
    private lateinit var watermarkName: TextView
    private lateinit var watermarkPhone: TextView
    private lateinit var logoView: ImageView
    private lateinit var ticker: TextView
    private lateinit var bitrateText: TextView
    private lateinit var editor: WatermarkEditor
    private val logoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            logoView.setImageURI(uri)
            logoView.visibility = View.VISIBLE
            prefs.edit().putString("logo_uri_$activeProfile", uri.toString()).apply()
            statusText.text = "Logo applied to watermark."
        }
    }

    private val overlayPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            editor.addImage(uri)
            editor.begin()
            statusText.text = "Image overlay added."
        }
    }

    private var exportTemplateName = ""
    private val exportTemplateLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && exportTemplateName.isNotBlank()) {
            val json = editor.exportTemplate(exportTemplateName)
            if (json != null) runCatching {
                contentResolver.openOutputStream(uri)?.use { OutputStreamWriter(it).use { w -> w.write(json) } }
                statusText.text = "Template exported: $exportTemplateName"
            }.onFailure { statusText.text = "Template export failed: ${it.localizedMessage}" }
        }
    }

    private val importTemplateLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val json = contentResolver.openInputStream(uri)?.use { InputStreamReader(it).use { r -> r.readText() } }
            val name = json?.let { editor.importTemplate(it) }
            statusText.text = if (name != null) "Template imported: $name" else "Invalid template file."
        }.onFailure { statusText.text = "Template import failed: ${it.localizedMessage}" }
    }

    private val prefs by lazy { getSharedPreferences("studio_profiles", MODE_PRIVATE) }
    private val profiles = linkedMapOf<String, Pair<String, String>>()
    private var activeProfile = "Asian Press"
    private var streamServer = ""
    private var streamKey = ""
    private var lastRecordPath = ""
    private var qualityWidth = 1280
    private var qualityHeight = 720
    private var qualityBitrate = 2_500_000
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockUpdater = object : Runnable {
        override fun run() {
            for (i in 0 until watermarkLayer.childCount) {
                val v = watermarkLayer.getChildAt(i)
                if (v.tag?.toString() == "custom_datetime" && v is TextView) {
                    v.text = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                }
            }
            clockHandler.postDelayed(this, 1000)
        }
    }

    private val rtmpStream: RtmpStream by lazy {
        RtmpStream(this, this).apply {
            getGlInterface().autoHandleOrientation = true
            getStreamClient().setBitrateExponentialFactor(0.5f)
            getStreamClient().setReTries(10)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.CAMERA] == true && result[Manifest.permission.RECORD_AUDIO] == true) {
            prepareStreamEngine()
        } else statusText.text = "Camera and microphone permissions are required."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        loadQuality()
        loadProfiles()
        applyProfile()
        editor = WatermarkEditor(this, watermarkLayer, prefs)
        restoreLogo()
        setupButtons()
        clockHandler.post(clockUpdater)
        requestPermissionsIfNeeded()
    }

    private fun bindViews() {
        openGlView = findViewById(R.id.openGlView)
        watermarkLayer = findViewById(R.id.watermarkLayer)
        statusText = findViewById(R.id.statusText)
        liveButton = findViewById(R.id.liveButton)
        recordButton = findViewById(R.id.recordButton)
        profileLabel = findViewById(R.id.profileLabel)
        watermarkName = findViewById(R.id.watermarkName)
        watermarkPhone = findViewById(R.id.watermarkPhone)
        logoView = findViewById(R.id.logoView)
        ticker = findViewById(R.id.ticker)
        bitrateText = findViewById(R.id.bitrateText)

        openGlView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!rtmpStream.isOnPreview) {
                    rtmpStream.startPreview(openGlView)
                }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                rtmpStream.getGlInterface().setPreviewResolution(width, height)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (rtmpStream.isOnPreview) rtmpStream.stopPreview()
            }
        })
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.switchCamera).setOnClickListener {
            try {
                rtmpStream.videoSource.switchCamera()
            } catch (e: Exception) {
                statusText.text = "Camera switch unavailable: ${e.localizedMessage}"
            }
        }
        recordButton.setOnClickListener { toggleRecording() }
        liveButton.setOnClickListener { if (rtmpStream.isStreaming) stopLive() else showLiveSetupDialog() }
        findViewById<Button>(R.id.profileButton).setOnClickListener { showProfileDialog() }
        findViewById<Button>(R.id.logoButton).setOnClickListener { showWatermarkEditor() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener { showQualityDialog() }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) prepareStreamEngine() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun prepareStreamEngine() {
        val prepared = try {
            rtmpStream.prepareVideo(qualityWidth, qualityHeight, qualityBitrate, 30, 2, 0) &&
                    rtmpStream.prepareAudio(44_100, true, 128_000, true, true)
        } catch (e: Exception) {
            statusText.text = "Encoder preparation failed: ${e.localizedMessage}"
            false
        }
        if (!prepared) {
            statusText.text = "This device could not prepare the live encoder."
            return
        }
        installRealtimeWatermark()
        statusText.text = "Camera ready • ${qualityLabel()} • 30 FPS"
    }

    private fun installRealtimeWatermark() {
        watermarkLayer.post {
            try {
                rtmpStream.getGlInterface().setFilter(
                    com.pedro.encoder.input.gl.render.filters.ViewFilterRender().apply {
                        view = watermarkLayer
                    }
                )
            } catch (e: Exception) {
                statusText.text = "Watermark engine unavailable: ${e.localizedMessage}"
            }
        }
    }

    private fun showLiveSetupDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 12, 36, 0) }
        val server = EditText(this).apply {
            hint = "RTMP server URL"
            setText(prefs.getString("rtmp_server", ""))
            singleLine = true
        }
        val key = EditText(this).apply {
            hint = "Stream key"
            inputType = 0x00000081
            singleLine = true
            setText("")
        }
        val quality = TextView(this).apply { text = "The key is kept in memory only in this V1 build."; setPadding(0, 12, 0, 0) }
        layout.addView(server); layout.addView(key); layout.addView(quality)
        AlertDialog.Builder(this).setTitle("Start RTMP Live")
            .setMessage("For YouTube/Facebook, paste the ingest server URL and your current stream key. Never share your key with anyone.")
            .setView(layout)
            .setPositiveButton("GO LIVE") { _, _ ->
                val base = server.text.toString().trim().trimEnd('/')
                val secret = key.text.toString().trim()
                if (!base.startsWith("rtmp://") && !base.startsWith("rtmps://")) {
                    statusText.text = "Enter a valid RTMP/RTMPS server URL."
                    return@setPositiveButton
                }
                if (secret.isBlank()) {
                    statusText.text = "Stream key is required."
                    return@setPositiveButton
                }
                streamServer = base
                streamKey = secret
                prefs.edit().putString("rtmp_server", base).apply()
                startLive()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun startLive() {
        if (rtmpStream.isStreaming) return
        val endpoint = if (streamKey.startsWith("/")) streamServer + streamKey else "$streamServer/$streamKey"
        try {
            rtmpStream.startStream(endpoint)
            liveButton.text = "Stop Live"
            statusText.text = "Connecting to live server…"
            findViewById<TextView>(R.id.liveBadge).text = "● CONNECTING"
        } catch (e: Exception) {
            statusText.text = "Could not start live: ${e.localizedMessage}"
        }
    }

    private fun stopLive() {
        try { rtmpStream.stopStream() } catch (_: Exception) { }
        liveButton.text = "Go Live"
        findViewById<TextView>(R.id.liveBadge).text = "● LIVE PREVIEW"
        statusText.text = "Live stream stopped"
        bitrateText.text = ""
    }

    private fun toggleRecording() {
        if (rtmpStream.isRecording) {
            try { rtmpStream.stopRecord() } catch (_: Exception) { }
            recordButton.text = "Record"
            statusText.text = "Recording saved: $lastRecordPath"
            return
        }
        val dir = File(getExternalFilesDir("Movies"), "AsianLiveStudio")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        lastRecordPath = File(dir, "AsianLive_$stamp.mp4").absolutePath
        try {
            rtmpStream.startRecord(lastRecordPath) { state ->
                runOnUiThread {
                    if (state == RecordController.Status.RECORDING) {
                        recordButton.text = "Stop Record"
                        statusText.text = "Recording with live watermark…"
                    }
                }
            }
        } catch (e: Exception) {
            statusText.text = "Recording failed: ${e.localizedMessage}"
        }
    }

    override fun onConnectionStarted(url: String) { runOnUiThread { statusText.text = "Connecting…" } }
    override fun onConnectionSuccess() = runOnUiThread {
        liveButton.text = "Stop Live"
        findViewById<TextView>(R.id.liveBadge).text = "● LIVE"
        statusText.text = "LIVE • Connected"
    }
    override fun onConnectionFailed(reason: String) = runOnUiThread {
        liveButton.text = "Go Live"
        findViewById<TextView>(R.id.liveBadge).text = "● LIVE PREVIEW"
        statusText.text = "Live connection failed: $reason"
    }
    override fun onNewBitrate(bitrate: Long) = runOnUiThread {
        bitrateText.text = String.format(Locale.getDefault(), "%.1f Mbps", bitrate / 1_000_000f)
    }
    override fun onDisconnect() = runOnUiThread {
        liveButton.text = "Go Live"
        findViewById<TextView>(R.id.liveBadge).text = "● LIVE PREVIEW"
        statusText.text = "Disconnected from live server"
    }
    override fun onAuthError() = runOnUiThread { statusText.text = "RTMP authentication failed."; stopLive() }
    override fun onAuthSuccess() = Unit

    private fun showQualityDialog() {
        val labels = arrayOf("480p • 30 FPS • 1.2 Mbps", "720p • 30 FPS • 2.5 Mbps", "1080p • 30 FPS • 4.0 Mbps")
        AlertDialog.Builder(this).setTitle("Video quality").setItems(labels) { _, which ->
            val values = when (which) {
                0 -> intArrayOf(854, 480, 1_200_000)
                2 -> intArrayOf(1920, 1080, 4_000_000)
                else -> intArrayOf(1280, 720, 2_500_000)
            }
            if (rtmpStream.isStreaming || rtmpStream.isRecording) {
                statusText.text = "Stop live/recording before changing quality."
                return@setItems
            }
            qualityWidth = values[0]; qualityHeight = values[1]; qualityBitrate = values[2]
            prefs.edit().putInt("quality_width", qualityWidth).putInt("quality_height", qualityHeight).putInt("quality_bitrate", qualityBitrate).apply()
            if (rtmpStream.isOnPreview) rtmpStream.stopPreview()
            val ok = runCatching {
                rtmpStream.prepareVideo(qualityWidth, qualityHeight, qualityBitrate, 30, 2, 0) &&
                    rtmpStream.prepareAudio(44_100, true, 128_000, true, true)
            }.getOrDefault(false)
            if (ok) {
                if (openGlView.holder.surface.isValid) rtmpStream.startPreview(openGlView)
                installRealtimeWatermark()
                statusText.text = "Quality changed to ${qualityLabel()}"
            } else {
                statusText.text = "Device rejected ${labels[which]}. Try 720p."
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun loadQuality() {
        qualityWidth = prefs.getInt("quality_width", 1280)
        qualityHeight = prefs.getInt("quality_height", 720)
        qualityBitrate = prefs.getInt("quality_bitrate", 2_500_000)
    }

    private fun qualityLabel(): String = when (qualityHeight) {
        480 -> "480p / 1.2 Mbps"
        1080 -> "1080p / 4 Mbps"
        else -> "720p / 2.5 Mbps"
    }

    private fun restoreLogo() {
        prefs.getString("logo_uri_$activeProfile", null)?.let {
            try { logoView.setImageURI(Uri.parse(it)); logoView.visibility = View.VISIBLE } catch (_: Exception) {}
        }
    }

    private fun showWatermarkEditor() {
        val options = arrayOf(
            "Edit watermark on canvas", "Add text layer", "Add LIVE badge", "Add image overlay", "Add date/time",
            "Choose logo", "Make selected larger", "Make selected smaller", "Duplicate selected",
            "Bring forward", "Send backward", "Delete selected", "Text style", "Opacity", "Position",
            "Save template", "Load template", "Delete template", "Export template", "Import template"
        )
        AlertDialog.Builder(this).setTitle("Watermark Editor").setItems(options) { _, which ->
            when (which) {
                0 -> { editor.begin(); statusText.text = "Watermark editor active • drag layers on preview" }
                1 -> {
                    val input = EditText(this).apply { hint = "Text layer" }
                    AlertDialog.Builder(this).setTitle("Add text layer").setView(input).setPositiveButton("Add") { _, _ -> editor.addText(input.text.toString().ifBlank { "ASIAN PRESS" }); editor.begin() }.setNegativeButton("Cancel", null).show()
                }
                2 -> { editor.addBadge(); editor.begin() }
                3 -> overlayPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                4 -> { editor.addTimestamp(); editor.begin() }
                5 -> logoPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                6 -> editor.scaleSelected(1.15f)
                7 -> editor.scaleSelected(0.87f)
                8 -> editor.duplicateSelected()
                9 -> editor.bringForward()
                10 -> editor.sendBackward()
                11 -> editor.deleteSelected()
                12 -> showTextStyleDialog()
                13 -> showOpacityDialog()
                14 -> showPositionDialog()
                15 -> showSaveTemplateDialog()
                16 -> showLoadTemplateDialog()
                17 -> showDeleteTemplateDialog()
                18 -> showExportTemplateDialog()
                19 -> importTemplateLauncher.launch(arrayOf("application/json", "text/*"))
            }
        }.setNegativeButton("Close") { _, _ -> editor.end(); statusText.text = "Watermark editor closed" }.show()
    }

    private fun saveTemplate() {
        showSaveTemplateDialog()
    }

    private fun showSaveTemplateDialog() {
        val input = EditText(this).apply {
            hint = "Template name"
            setText("${activeProfile} Default")
            selectAll()
        }
        AlertDialog.Builder(this).setTitle("Save watermark template")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { statusText.text = "Template name is required."; return@setPositiveButton }
                editor.saveTemplate(name)
                statusText.text = "Template saved: $name"
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showLoadTemplateDialog() {
        val names = editor.templateNames()
        if (names.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Load template").setMessage("No saved watermark templates yet.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Load watermark template").setItems(names.toTypedArray()) { _, which ->
            val name = names[which]
            if (editor.loadTemplate(name)) statusText.text = "Template loaded: $name"
            else statusText.text = "Could not load template: $name"
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showDeleteTemplateDialog() {
        val names = editor.templateNames()
        if (names.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Delete template").setMessage("No saved templates.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Delete watermark template").setItems(names.toTypedArray()) { _, which ->
            val name = names[which]
            AlertDialog.Builder(this).setTitle("Delete $name?").setMessage("This cannot be undone.")
                .setPositiveButton("DELETE") { _, _ -> editor.deleteTemplate(name); statusText.text = "Template deleted: $name" }
                .setNegativeButton("Cancel", null).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showTextStyleDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 12, 36, 0) }
        val size = EditText(this).apply { hint = "Text size (sp), e.g. 18"; inputType = 2; setText("18") }
        val color = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("White", "Yellow", "Red", "Cyan", "Black"))
        }
        val font = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("SANS", "SERIF", "MONOSPACE"))
        }
        val bg = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Black", "Transparent", "Blue", "Red"))
        }
        layout.addView(size); layout.addView(color); layout.addView(font); layout.addView(bg)
        AlertDialog.Builder(this).setTitle("Text style").setView(layout)
            .setPositiveButton("APPLY") { _, _ ->
                editor.setSelectedTextSize(size.text.toString().toFloatOrNull() ?: 18f)
                val c = when (color.selectedItemPosition) { 1 -> android.graphics.Color.YELLOW; 2 -> android.graphics.Color.RED; 3 -> android.graphics.Color.CYAN; 4 -> android.graphics.Color.BLACK; else -> android.graphics.Color.WHITE }
                editor.setSelectedTextColor(c)
                editor.setSelectedFont(font.selectedItem.toString())
                val bgColor = when (bg.selectedItemPosition) { 1 -> Color.TRANSPARENT; 2 -> 0xCC0B3D68.toInt(); 3 -> 0xCCB71C1C.toInt(); else -> 0x99000000.toInt() }
                editor.setSelectedBackground(bgColor)
                statusText.text = "Text style applied to selected layer."
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showOpacityDialog() {
        val input = EditText(this).apply { hint = "Opacity 10–100"; inputType = 2; setText("100") }
        AlertDialog.Builder(this).setTitle("Layer opacity").setView(input)
            .setPositiveButton("APPLY") { _, _ ->
                editor.setSelectedOpacity(input.text.toString().toIntOrNull() ?: 100)
                statusText.text = "Layer opacity updated."
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showPositionDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 12, 36, 0) }
        val x = EditText(this).apply { hint = "X position 0–100%"; inputType = 2; setText("5") }
        val y = EditText(this).apply { hint = "Y position 0–100%"; inputType = 2; setText("5") }
        layout.addView(x); layout.addView(y)
        AlertDialog.Builder(this).setTitle("Exact layer position").setView(layout)
            .setPositiveButton("APPLY") { _, _ -> editor.setSelectedPosition(x.text.toString().toIntOrNull() ?: 5, y.text.toString().toIntOrNull() ?: 5) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showExportTemplateDialog() {
        val names = editor.templateNames()
        if (names.isEmpty()) { statusText.text = "Save a template before exporting."; return }
        AlertDialog.Builder(this).setTitle("Export template").setItems(names.toTypedArray()) { _, which ->
            exportTemplateName = names[which]
            exportTemplateLauncher.launch("${exportTemplateName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showLogoInfo() {
        AlertDialog.Builder(this).setTitle("Watermark")
            .setMessage("Phase 4 uses the Android view as a real-time encoder filter. The logo, studio name, phone and ticker are therefore included in the live stream and in recordings made by the RootEncoder pipeline.")
            .setPositiveButton("OK", null).show()
    }

    private fun loadProfiles() {
        val names = prefs.getStringSet("profile_names", setOf("Asian Press"))?.toMutableSet() ?: mutableSetOf("Asian Press")
        names.forEach { name ->
            profiles[name] = (prefs.getString("business_$name", name) ?: name) to
                    (prefs.getString("phone_$name", "9865323570") ?: "")
        }
        if (profiles.isEmpty()) profiles["Asian Press"] = "ASIAN PRESS" to "9865323570"
        activeProfile = prefs.getString("active_profile", profiles.keys.first()) ?: profiles.keys.first()
        if (!profiles.containsKey(activeProfile)) activeProfile = profiles.keys.first()
    }

    private fun applyProfile() {
        val details = profiles[activeProfile] ?: ("ASIAN PRESS" to "9865323570")
        profileLabel.text = activeProfile
        watermarkName.text = details.first
        watermarkPhone.text = details.second
        ticker.text = prefs.getString("ticker_$activeProfile", "Asian Press • Photo • Video • Printing • 9865323570")
        prefs.edit().putString("active_profile", activeProfile).apply()
    }

    private fun showProfileDialog() {
        val names = profiles.keys.toTypedArray()
        val options = names + "＋ Add new studio"
        AlertDialog.Builder(this).setTitle("Select studio profile")
            .setItems(options) { _, which -> if (which < names.size) { activeProfile = names[which]; applyProfile(); editProfile() } else addProfile() }
            .setNeutralButton("Edit current") { _, _ -> editProfile() }.show()
    }

    private fun addProfile() {
        val input = EditText(this).apply { hint = "Studio / shop name" }
        AlertDialog.Builder(this).setTitle("New studio profile").setView(input)
            .setPositiveButton("Continue") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    profiles[name] = name to ""
                    prefs.edit().putStringSet("profile_names", profiles.keys.toSet()).apply()
                    activeProfile = name
                    editProfile()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun editProfile() {
        val current = profiles[activeProfile] ?: (activeProfile to "")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 8, 40, 0) }
        val business = EditText(this).apply { hint = "Business name"; setText(current.first) }
        val phone = EditText(this).apply { hint = "Phone number"; setText(current.second); inputType = 3 }
        val tickerInput = EditText(this).apply { hint = "Scrolling promotional text"; setText(prefs.getString("ticker_$activeProfile", "Asian Press • Photo • Video • Printing")) }
        layout.addView(business); layout.addView(phone); layout.addView(tickerInput)
        AlertDialog.Builder(this).setTitle("Edit $activeProfile").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                profiles[activeProfile] = business.text.toString() to phone.text.toString()
                prefs.edit().putString("business_$activeProfile", business.text.toString())
                    .putString("phone_$activeProfile", phone.text.toString())
                    .putString("ticker_$activeProfile", tickerInput.text.toString())
                    .putStringSet("profile_names", profiles.keys.toSet()).apply()
                applyProfile()
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockUpdater)
        try { rtmpStream.release() } catch (_: Exception) { }
        super.onDestroy()
    }
}
