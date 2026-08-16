package com.mlevngr.inknote

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.appearance.ThemeColors
import com.mlevngr.inknote.plugins.DiscoveredPlugin
import com.mlevngr.inknote.plugins.MotePluginHost
import com.mlevngr.inknote.plugins.PluginApprovalStore
import com.mlevngr.inknote.plugins.PluginCapabilityLabels
import com.mlevngr.inknote.ui.SystemBarInsets
import com.mlevngr.mote.plugin.api.PluginCapability
import com.mlevngr.mote.plugin.api.PluginDescriptor

class PluginManagerActivity : AppCompatActivity() {
    private lateinit var host: MotePluginHost
    private lateinit var list: LinearLayout
    private lateinit var approvals: PluginApprovalStore
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences(this).applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugin_manager)
        SystemBarInsets.install(findViewById(R.id.plugin_manager_root))
        approvals = PluginApprovalStore(this)
        list = findViewById(R.id.plugin_list)
        findViewById<MaterialToolbar>(R.id.plugin_manager_toolbar)
            .setNavigationOnClickListener { finish() }
        host = MotePluginHost(this, ::renderPlugins)
        host.start()
    }

    private fun renderPlugins(plugins: List<DiscoveredPlugin>) {
        val requestGeneration = ++generation
        list.removeAllViews()
        if (plugins.isEmpty()) {
            list.addView(messageView(getString(R.string.no_plugins_installed)))
            return
        }
        plugins.forEach { plugin ->
            val loading = pluginCard(plugin.label, getString(R.string.plugin_loading))
            list.addView(loading)
            host.loadDescriptor(
                plugin,
                onLoaded = { descriptor ->
                    if (requestGeneration != generation) return@loadDescriptor
                    val index = list.indexOfChild(loading)
                    if (index >= 0) {
                        list.removeViewAt(index)
                        list.addView(pluginCard(plugin, descriptor), index)
                    }
                },
                onError = { error ->
                    if (requestGeneration != generation) return@loadDescriptor
                    val index = list.indexOfChild(loading)
                    if (index >= 0) {
                        list.removeViewAt(index)
                        list.addView(pluginCard(plugin.label, error), index)
                    }
                }
            )
        }
    }

    private fun pluginCard(plugin: DiscoveredPlugin, descriptor: PluginDescriptor): MaterialCardView {
        val approved = approvals.isApproved(plugin.packageName, descriptor)
        val details = buildString {
            appendLine(descriptor.description)
            append(descriptor.capabilities.joinToString(" · ") { capabilityLabel(it) })
            appendLine()
            append(if (approved) getString(R.string.plugin_authorized) else getString(R.string.plugin_not_authorized))
        }.trim()
        return pluginCard(descriptor.label, details).apply {
            setOnClickListener { showPluginDetails(plugin, descriptor) }
        }
    }

    private fun pluginCard(title: String, details: String): MaterialCardView =
        MaterialCardView(this).apply {
            radius = 18.dp.toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp
            setStrokeColor(ThemeColors.resolve(this@PluginManagerActivity, com.google.android.material.R.attr.colorOutline))
            addView(LinearLayout(this@PluginManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp, 16.dp, 18.dp, 16.dp)
                addView(TextView(this@PluginManagerActivity).apply {
                    text = title
                    textSize = 18f
                    setTextColor(ThemeColors.resolve(this@PluginManagerActivity, R.attr.inkNoteTextPrimary))
                })
                addView(TextView(this@PluginManagerActivity).apply {
                    text = details
                    textSize = 14f
                    setPadding(0, 6.dp, 0, 0)
                    setTextColor(ThemeColors.resolve(this@PluginManagerActivity, R.attr.inkNoteTextSecondary))
                })
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp }
        }

    private fun messageView(message: String) = TextView(this).apply {
        text = message
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(24.dp, 64.dp, 24.dp, 24.dp)
        setTextColor(ThemeColors.resolve(this@PluginManagerActivity, R.attr.inkNoteTextSecondary))
    }

    private fun showPluginDetails(plugin: DiscoveredPlugin, descriptor: PluginDescriptor) {
        val approved = approvals.isApproved(plugin.packageName, descriptor)
        MaterialAlertDialogBuilder(this)
            .setTitle(descriptor.label)
            .setMessage(descriptor.capabilities.joinToString("\n") { "• ${capabilityLabel(it)}" })
            .apply {
                if (approved) {
                    setNegativeButton(R.string.plugin_revoke) { _, _ ->
                        approvals.revoke(plugin.packageName, descriptor)
                        host.refresh()
                    }
                }
                descriptor.settingsActivity?.let { activityName ->
                    setPositiveButton(R.string.plugin_settings) { _, _ ->
                        runCatching {
                            startActivity(
                                Intent().setComponent(ComponentName(plugin.packageName, activityName))
                            )
                        }
                    }
                }
            }
            .setNeutralButton(android.R.string.ok, null)
            .show()
    }

    private fun capabilityLabel(capability: PluginCapability): String =
        PluginCapabilityLabels.label(capability)

    override fun onDestroy() {
        if (::host.isInitialized) host.close()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
