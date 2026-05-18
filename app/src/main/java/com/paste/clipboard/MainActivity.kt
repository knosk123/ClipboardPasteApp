package com.paste.clipboard

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnStartService: Button
    private lateinit var etCustomText: EditText
    private lateinit var rvHistory: RecyclerView
    private lateinit var historyManager: HistoryManager
    private lateinit var pasteTextStore: PasteTextStore
    private lateinit var historyAdapter: HistoryAdapter
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        historyManager = HistoryManager(this)
        pasteTextStore = PasteTextStore(this)

        initViews()
        setupListeners()
        updateStatus()
        setupHistory()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshHistory()
    }

    private fun initViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnStartService = findViewById(R.id.btnStartService)
        etCustomText = findViewById(R.id.etCustomText)
        rvHistory = findViewById(R.id.rvHistory)
    }

    private fun setupListeners() {
        btnAccessibility.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.toast_accessibility_settings_failed, Toast.LENGTH_SHORT).show()
            }
        }

        btnOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        btnStartService.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, R.string.toast_accessibility_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.toast_overlay_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            requestNotificationPermissionIfNeeded()

            val textToPaste = resolveTextToPaste()
            if (textToPaste.isBlank()) {
                Toast.makeText(this, R.string.toast_no_text_to_paste, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pasteTextStore.save(textToPaste)

            if (!FloatingWindowService.isRunning) {
                val intent = Intent(this, FloatingWindowService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, R.string.toast_floating_window_started, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_text_prepared, Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }
    }

    private fun updateStatus() {
        val a11yEnabled = isAccessibilityServiceEnabled()
        tvAccessibilityStatus.text = getString(
            if (a11yEnabled) R.string.accessibility_enabled else R.string.accessibility_disabled
        )
        tvAccessibilityStatus.setTextColor(
            if (a11yEnabled) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        )
        btnAccessibility.text = getString(if (a11yEnabled) R.string.enabled else R.string.open_settings)
        btnAccessibility.isEnabled = !a11yEnabled

        val overlayEnabled = Settings.canDrawOverlays(this)
        tvOverlayStatus.text = getString(
            if (overlayEnabled) R.string.overlay_enabled else R.string.overlay_disabled
        )
        tvOverlayStatus.setTextColor(
            if (overlayEnabled) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        )
        btnOverlay.text = getString(if (overlayEnabled) R.string.granted else R.string.grant_permission)
        btnOverlay.isEnabled = !overlayEnabled

        btnStartService.text = getString(
            if (FloatingWindowService.isRunning) R.string.update_paste_text else R.string.start_floating_window
        )
    }

    private fun setupHistory() {
        historyAdapter = HistoryAdapter(
            historyManager.getAll().toMutableList(),
            onUse = { text ->
                etCustomText.setText(text)
                etCustomText.setSelection(text.length)
            }
        )
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
    }

    private fun refreshHistory() {
        historyAdapter.updateData(historyManager.getAll())
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun resolveTextToPaste(): String {
        val customText = etCustomText.text.toString()
        if (customText.isNotBlank()) return customText

        return try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this).toString()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${PasteAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        return splitter.any { it.equals(expectedService, ignoreCase = true) }
    }
}

class HistoryAdapter(
    private val items: MutableList<String>,
    private val onUse: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tvHistoryText)
        val btnUse: Button = view.findViewById(R.id.btnHistoryUse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = items[position]
        holder.tvText.text = text
        holder.btnUse.setOnClickListener { onUse(text) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<String>) {
        val oldItems = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size
            override fun getNewListSize() = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition] == newItems[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition] == newItems[newItemPosition]
            }
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }
}
