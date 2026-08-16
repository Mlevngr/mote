package com.mlevngr.inknote

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.sync.WebDavConfig
import com.mlevngr.inknote.sync.WebDavEndpoint
import com.mlevngr.inknote.sync.WebDavPreferences
import com.mlevngr.inknote.sync.WebDavSyncManager
import com.mlevngr.inknote.ui.SystemBarInsets
import java.util.concurrent.Executors

class WebDavSettingsActivity : AppCompatActivity() {
    private lateinit var preferences: WebDavPreferences
    private lateinit var saveAndTest: MaterialButton
    private lateinit var syncNow: MaterialButton
    private lateinit var status: TextView
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences(this).applyTheme(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_webdav_settings)
        SystemBarInsets.install(findViewById(R.id.webdav_root), avoidIme = true)

        preferences = WebDavPreferences(this)
        findViewById<MaterialToolbar>(R.id.webdav_toolbar).setNavigationOnClickListener { finish() }
        saveAndTest = findViewById(R.id.webdav_save_test)
        syncNow = findViewById(R.id.webdav_sync_now)
        status = findViewById(R.id.webdav_status)
        populate(preferences.load())
        saveAndTest.setOnClickListener { saveAndTest() }
        syncNow.setOnClickListener { syncNow() }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun populate(config: WebDavConfig) {
        edit(R.id.webdav_internal_url).setText(config.internalUrl)
        edit(R.id.webdav_external_url).setText(config.externalUrl)
        edit(R.id.webdav_remote_folder).setText(config.remoteFolder)
        edit(R.id.webdav_username).setText(config.username)
        edit(R.id.webdav_password).setText(config.password)
    }

    private fun saveAndTest() {
        val config = readAndSave() ?: return
        runOperation(getString(R.string.webdav_testing)) {
            val results = WebDavSyncManager(this).testConnections(config)
            results.joinToString("\n") { result ->
                val endpoint = endpointLabel(result.endpoint.kind)
                getString(
                    if (result.available) R.string.webdav_test_success else R.string.webdav_test_failed,
                    endpoint,
                    result.message
                )
            }
        }
    }

    private fun syncNow() {
        val config = readAndSave() ?: return
        runOperation(getString(R.string.webdav_syncing)) {
            val report = WebDavSyncManager(this).sync(config)
            getString(
                R.string.webdav_sync_result,
                endpointLabel(report.endpoint.kind),
                report.uploaded,
                report.downloaded,
                report.deletedLocal,
                report.deletedRemote,
                report.conflicts
            )
        }
    }

    private fun readAndSave(): WebDavConfig? {
        clearErrors()
        val raw = WebDavConfig(
            internalUrl = value(R.id.webdav_internal_url),
            externalUrl = value(R.id.webdav_external_url),
            remoteFolder = value(R.id.webdav_remote_folder),
            username = value(R.id.webdav_username),
            password = value(R.id.webdav_password)
        )
        return runCatching { preferences.save(raw) }.fold(
            onSuccess = { saved -> populate(saved); saved },
            onFailure = { error ->
                val message = error.message ?: getString(R.string.webdav_invalid_config)
                when {
                    "外网" in message -> layout(R.id.webdav_external_layout).error = message
                    "内网" in message || "地址" in message -> layout(R.id.webdav_internal_layout).error = message
                    "目录" in message -> layout(R.id.webdav_folder_layout).error = message
                    else -> status.text = message
                }
                null
            }
        )
    }

    private fun runOperation(progress: String, operation: () -> String) {
        setBusy(true)
        status.text = progress
        io.execute {
            val result = runCatching(operation)
            main.post {
                if (isFinishing || isDestroyed) return@post
                setBusy(false)
                status.text = result.getOrElse(WebDavSyncManager::safeMessage)
            }
        }
    }

    private fun clearErrors() {
        listOf(
            R.id.webdav_internal_layout,
            R.id.webdav_external_layout,
            R.id.webdav_folder_layout,
            R.id.webdav_username_layout,
            R.id.webdav_password_layout
        ).forEach { layout(it).error = null }
    }

    private fun setBusy(busy: Boolean) {
        saveAndTest.isEnabled = !busy
        syncNow.isEnabled = !busy
    }

    private fun endpointLabel(kind: WebDavEndpoint.Kind): String = getString(
        if (kind == WebDavEndpoint.Kind.Internal) R.string.webdav_internal else R.string.webdav_external
    )

    private fun edit(id: Int): TextInputEditText = findViewById(id)
    private fun layout(id: Int): TextInputLayout = findViewById(id)
    private fun value(id: Int): String = edit(id).text?.toString().orEmpty()
}
