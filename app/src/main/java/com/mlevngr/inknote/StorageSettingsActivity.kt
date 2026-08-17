package com.mlevngr.inknote

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.storage.SharedStorageManager
import com.mlevngr.inknote.sync.WebDavSyncReport
import com.mlevngr.inknote.ui.SystemBarInsets
import java.util.concurrent.Executors

class StorageSettingsActivity : AppCompatActivity() {
    private lateinit var manager: SharedStorageManager
    private lateinit var currentFolder: TextView
    private lateinit var chooseFolder: MaterialButton
    private lateinit var syncNow: MaterialButton
    private lateinit var status: TextView
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(::configureFolder)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences(this).applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage_settings)
        SystemBarInsets.install(findViewById(R.id.storage_settings_root))

        manager = SharedStorageManager(this)
        currentFolder = findViewById(R.id.shared_storage_current)
        chooseFolder = findViewById<MaterialButton>(R.id.shared_storage_choose).also {
            it.setOnClickListener { openTree.launch(manager.treeUri) }
        }
        syncNow = findViewById<MaterialButton>(R.id.shared_storage_sync).also {
            it.setOnClickListener { synchronize() }
        }
        status = findViewById(R.id.shared_storage_status)
        findViewById<MaterialToolbar>(R.id.storage_settings_toolbar)
            .setNavigationOnClickListener { finish() }
        updateCurrentFolder()
    }

    override fun onResume() {
        super.onResume()
        if (::manager.isInitialized) updateCurrentFolder()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun configureFolder(uri: Uri) {
        runOperation { manager.configure(uri) }
    }

    private fun synchronize() {
        if (manager.treeUri == null) {
            status.setText(R.string.shared_storage_choose_first)
            return
        }
        runOperation { requireNotNull(manager.syncIfConfigured()) }
    }

    private fun runOperation(operation: () -> WebDavSyncReport) {
        setBusy(true)
        status.setText(R.string.shared_storage_syncing)
        io.execute {
            val result = runCatching(operation)
            main.post {
                if (isFinishing || isDestroyed) return@post
                setBusy(false)
                result.fold(
                    onSuccess = { report ->
                        updateCurrentFolder()
                        status.text = getString(
                            R.string.shared_storage_sync_result,
                            report.uploaded,
                            report.downloaded,
                            report.deletedRemote + report.deletedLocal,
                            report.conflicts
                        )
                    },
                    onFailure = { error ->
                        status.text = error.message ?: getString(R.string.shared_storage_failed)
                    }
                )
            }
        }
    }

    private fun updateCurrentFolder() {
        val name = runCatching { manager.displayName() }.getOrNull()
        currentFolder.text = if (manager.treeUri == null) {
            getString(R.string.shared_storage_not_configured)
        } else {
            getString(R.string.shared_storage_selected, name ?: manager.treeUri.toString())
        }
        syncNow.isEnabled = manager.treeUri != null
    }

    private fun setBusy(busy: Boolean) {
        chooseFolder.isEnabled = !busy
        syncNow.isEnabled = !busy && manager.treeUri != null
    }
}
