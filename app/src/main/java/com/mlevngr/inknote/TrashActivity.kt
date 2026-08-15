package com.mlevngr.inknote

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.TrashPreferences
import com.mlevngr.inknote.library.TrashRetention
import com.mlevngr.inknote.ui.SystemBarInsets
import com.mlevngr.inknote.ui.TrashAdapter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TrashActivity : AppCompatActivity() {
    private lateinit var library: NoteLibrary
    private lateinit var preferences: TrashPreferences
    private lateinit var adapter: TrashAdapter
    private lateinit var emptyView: TextView
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val revision = AtomicInteger()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences(this).applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)
        SystemBarInsets.install(findViewById(R.id.trash_root))

        library = NoteLibrary(this)
        preferences = TrashPreferences(this)
        adapter = TrashAdapter(this, { preferences.retention.durationMillis }, ::showActions)
        emptyView = findViewById(R.id.empty_trash)
        findViewById<RecyclerView>(R.id.trash_list).apply {
            layoutManager = LinearLayoutManager(this@TrashActivity)
            adapter = this@TrashActivity.adapter
            itemAnimator = null
        }
        findViewById<MaterialToolbar>(R.id.trash_toolbar).apply {
            setNavigationIcon(R.drawable.ic_arrow_back_24)
            navigationContentDescription = getString(R.string.back_to_library)
            setNavigationOnClickListener { finish() }
        }
        findViewById<AppCompatImageButton>(R.id.trash_retention).setOnClickListener {
            showRetentionDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        val currentRevision = revision.incrementAndGet()
        val retention = preferences.retention.durationMillis
        io.execute {
            library.cleanupExpiredTrash(retention)
            val entries = library.listTrash()
            main.post {
                if (currentRevision != revision.get() || isFinishing || isDestroyed) return@post
                adapter.submit(entries)
                emptyView.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun showActions(entry: NoteLibrary.TrashEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.name)
            .setItems(arrayOf(getString(R.string.restore), getString(R.string.delete_permanently))) { _, which ->
                if (which == 0) restore(entry) else confirmPermanentDelete(entry)
            }
            .show()
    }

    private fun restore(entry: NoteLibrary.TrashEntry) {
        runOperation(R.string.restored) { library.restoreTrashEntry(entry.id) }
    }

    private fun confirmPermanentDelete(entry: NoteLibrary.TrashEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_permanently)
            .setMessage(getString(R.string.permanent_delete_confirmation, entry.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                runOperation(R.string.permanently_deleted) { library.permanentlyDeleteTrashEntry(entry.id) }
            }
            .show()
    }

    private fun showRetentionDialog() {
        val options = TrashRetention.entries
        val labels = arrayOf(
            getString(R.string.retention_7_days),
            getString(R.string.retention_30_days),
            getString(R.string.retention_90_days)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trash_retention)
            .setSingleChoiceItems(labels, options.indexOf(preferences.retention)) { dialog, which ->
                preferences.retention = options[which]
                dialog.dismiss()
                refresh()
            }
            .show()
    }

    private fun runOperation(successMessage: Int, operation: () -> Unit) {
        io.execute {
            val result = runCatching(operation)
            main.post {
                if (isFinishing || isDestroyed) return@post
                result.onSuccess {
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
                    refresh()
                }.onFailure {
                    Toast.makeText(this, it.message ?: getString(R.string.unknown_error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
