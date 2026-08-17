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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.backup.VaultBackupManager
import com.mlevngr.inknote.backup.BackupAttempt
import com.mlevngr.inknote.storage.SharedStorageManager
import com.mlevngr.inknote.ui.SystemBarInsets
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class DataSafetyActivity : AppCompatActivity() {
    private lateinit var manager: VaultBackupManager
    private lateinit var backupFolder: TextView
    private lateinit var lastBackup: TextView
    private lateinit var status: TextView
    private val operationButtons = mutableListOf<MaterialButton>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val chooseBackupFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(::configureBackupFolder) }

    private val exportVault = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            runOperation(R.string.exporting_vault) {
                manager.exportVault(it)
                getString(R.string.export_completed)
            }
        }
    }

    private val chooseRestoreArchive = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::confirmRestore) }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences(this).applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_safety)
        SystemBarInsets.install(findViewById(R.id.data_safety_root))
        manager = VaultBackupManager(this)
        backupFolder = findViewById(R.id.backup_folder_current)
        lastBackup = findViewById(R.id.last_backup)
        status = findViewById(R.id.data_safety_status)
        findViewById<MaterialToolbar>(R.id.data_safety_toolbar)
            .setNavigationOnClickListener { finish() }

        bindButton(R.id.choose_backup_folder) {
            chooseBackupFolder.launch(manager.backupTreeUri)
        }
        bindButton(R.id.backup_now) {
            if (manager.backupTreeUri == null) {
                status.setText(R.string.choose_backup_folder_first)
            } else {
                runOperation(R.string.creating_backup) {
                    val result = manager.createBackup()
                    getString(R.string.backup_created, result.fileName, result.retainedCount)
                }
            }
        }
        bindButton(R.id.cleanup_backups) {
            if (manager.backupTreeUri == null) {
                status.setText(R.string.choose_backup_folder_first)
            } else {
                runOperation(R.string.cleaning_backups) {
                    getString(R.string.backups_cleaned, manager.cleanupOldBackups())
                }
            }
        }
        bindButton(R.id.export_vault) { exportVault.launch(exportFileName()) }
        bindButton(R.id.restore_vault) {
            chooseRestoreArchive.launch(arrayOf("application/zip", "application/octet-stream"))
        }
        updateSummary()
    }

    override fun onResume() {
        super.onResume()
        if (::manager.isInitialized) updateSummary()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun bindButton(id: Int, action: () -> Unit) {
        findViewById<MaterialButton>(id).also { button ->
            operationButtons += button
            button.setOnClickListener { action() }
        }
    }

    private fun configureBackupFolder(uri: Uri) {
        runOperation(R.string.configuring_backup_folder) {
            manager.configureBackupFolder(uri)
            getString(R.string.backup_folder_configured)
        }
    }

    private fun confirmRestore(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_vault)
            .setMessage(R.string.restore_vault_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.restore_now) { _, _ ->
                runOperation(R.string.restoring_vault, finishOnSuccess = true) {
                    manager.restoreVault(uri)
                    runCatching { SharedStorageManager(applicationContext).syncIfConfigured() }
                    getString(R.string.restore_completed)
                }
            }
            .show()
    }

    private fun runOperation(
        progressMessage: Int,
        finishOnSuccess: Boolean = false,
        operation: () -> Any?
    ) {
        setBusy(true)
        status.setText(progressMessage)
        io.execute {
            val result = runCatching(operation)
            main.post {
                if (isFinishing || isDestroyed) return@post
                setBusy(false)
                result.fold(
                    onSuccess = { value ->
                        updateSummary()
                        status.text = value?.toString().orEmpty()
                        if (finishOnSuccess) {
                            setResult(RESULT_OK)
                            status.postDelayed({ if (!isFinishing) finish() }, 600L)
                        }
                    },
                    onFailure = { error ->
                        status.text = error.message ?: getString(R.string.data_operation_failed)
                    }
                )
            }
        }
    }

    private fun updateSummary() {
        val name = runCatching { manager.backupFolderDisplayName() }.getOrNull()
        backupFolder.text = if (manager.backupTreeUri == null) {
            getString(R.string.backup_folder_not_configured)
        } else {
            getString(R.string.backup_folder_selected, name ?: manager.backupTreeUri.toString())
        }
        lastBackup.text = backupAttemptSummary(manager.lastBackupAttempt)
    }

    private fun backupAttemptSummary(attempt: BackupAttempt?): String {
        if (attempt == null) return getString(R.string.no_backup_yet)
        val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(attempt.attemptedAt))
        return when (attempt.status) {
            BackupAttempt.Status.SUCCESS -> getString(
                R.string.last_manual_backup_succeeded,
                time,
                attempt.fileName.orEmpty()
            )
            BackupAttempt.Status.FAILED -> getString(
                R.string.last_manual_backup_failed,
                time,
                attempt.errorMessage ?: getString(R.string.data_operation_failed)
            )
            BackupAttempt.Status.IN_PROGRESS -> getString(
                R.string.last_manual_backup_interrupted,
                time
            )
        }
    }

    private fun setBusy(busy: Boolean) {
        operationButtons.forEach { it.isEnabled = !busy }
    }

    private fun exportFileName(): String = "Mote-vault-${
        SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ROOT).format(Date())
    }.zip"
}
