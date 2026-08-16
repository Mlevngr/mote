package com.mlevngr.mote.plugin.api

import android.os.Bundle

object PluginBundles {
    fun descriptor(value: PluginDescriptor): Bundle = Bundle().apply {
        putInt(PluginContract.KEY_API_VERSION, value.apiVersion)
        putString(PluginContract.KEY_PLUGIN_ID, value.pluginId)
        putString(PluginContract.KEY_LABEL, value.label)
        putString(PluginContract.KEY_DESCRIPTION, value.description)
        putStringArrayList(
            PluginContract.KEY_CAPABILITIES,
            ArrayList(value.capabilities.map(Enum<*>::name))
        )
        putParcelableArrayList(
            PluginContract.KEY_ACTIONS,
            ArrayList(value.actions.map(::action))
        )
        putString(PluginContract.KEY_SETTINGS_ACTIVITY, value.settingsActivity)
    }

    fun parseDescriptor(bundle: Bundle): PluginDescriptor? = runCatching {
        bundle.classLoader = PluginBundles::class.java.classLoader
        PluginDescriptor(
            apiVersion = bundle.getInt(PluginContract.KEY_API_VERSION),
            pluginId = bundle.requireString(PluginContract.KEY_PLUGIN_ID),
            label = bundle.requireString(PluginContract.KEY_LABEL),
            description = bundle.getString(PluginContract.KEY_DESCRIPTION).orEmpty(),
            capabilities = bundle.getStringArrayList(PluginContract.KEY_CAPABILITIES).orEmpty()
                .mapNotNull { runCatching { PluginCapability.valueOf(it) }.getOrNull() }
                .toSet(),
            actions = @Suppress("DEPRECATION")
            (bundle.getParcelableArrayList<Bundle>(PluginContract.KEY_ACTIONS))
                .orEmpty().mapNotNull(::parseAction),
            settingsActivity = bundle.getString(PluginContract.KEY_SETTINGS_ACTIVITY)
        )
    }.getOrNull()

    fun request(value: PluginRequest): Bundle = Bundle().apply {
        putString(PluginContract.KEY_SESSION_ID, value.sessionId)
        putString(PluginContract.KEY_REQUEST_ID, value.requestId)
        putString(PluginContract.KEY_ACTION_ID, value.actionId)
        putString(PluginContract.KEY_BASE_REVISION, value.baseRevision)
        putString(PluginContract.KEY_NOTE_TITLE, value.noteTitle)
        putString(PluginContract.KEY_MARKDOWN, value.markdown)
        putInt(PluginContract.KEY_SELECTION_START, value.selectionStart)
        putInt(PluginContract.KEY_SELECTION_END, value.selectionEnd)
    }

    fun parseRequest(bundle: Bundle): PluginRequest? = runCatching {
        PluginRequest(
            sessionId = bundle.requireString(PluginContract.KEY_SESSION_ID),
            requestId = bundle.requireString(PluginContract.KEY_REQUEST_ID),
            actionId = bundle.requireString(PluginContract.KEY_ACTION_ID),
            baseRevision = bundle.requireString(PluginContract.KEY_BASE_REVISION),
            noteTitle = bundle.getString(PluginContract.KEY_NOTE_TITLE).orEmpty(),
            markdown = bundle.requireString(PluginContract.KEY_MARKDOWN),
            selectionStart = bundle.getInt(PluginContract.KEY_SELECTION_START, -1),
            selectionEnd = bundle.getInt(PluginContract.KEY_SELECTION_END, -1)
        )
    }.getOrNull()

    fun result(value: PluginResult): Bundle = Bundle().apply {
        putString(PluginContract.KEY_SESSION_ID, value.sessionId)
        putString(PluginContract.KEY_REQUEST_ID, value.requestId)
        putString(PluginContract.KEY_BASE_REVISION, value.baseRevision)
        putString(PluginContract.KEY_MARKDOWN, value.markdown)
        putString(PluginContract.KEY_RESULT_SUMMARY, value.summary)
    }

    fun parseResult(bundle: Bundle): PluginResult? = runCatching {
        PluginResult(
            sessionId = bundle.requireString(PluginContract.KEY_SESSION_ID),
            requestId = bundle.requireString(PluginContract.KEY_REQUEST_ID),
            baseRevision = bundle.requireString(PluginContract.KEY_BASE_REVISION),
            markdown = bundle.requireString(PluginContract.KEY_MARKDOWN),
            summary = bundle.getString(PluginContract.KEY_RESULT_SUMMARY).orEmpty()
        )
    }.getOrNull()

    fun error(value: PluginError): Bundle = Bundle().apply {
        putString(PluginContract.KEY_SESSION_ID, value.sessionId)
        putString(PluginContract.KEY_REQUEST_ID, value.requestId)
        putString(PluginContract.KEY_ERROR_CODE, value.code)
        putString(PluginContract.KEY_ERROR_MESSAGE, value.message)
    }

    fun parseError(bundle: Bundle): PluginError? = runCatching {
        PluginError(
            sessionId = bundle.getString(PluginContract.KEY_SESSION_ID).orEmpty(),
            requestId = bundle.getString(PluginContract.KEY_REQUEST_ID).orEmpty(),
            code = bundle.getString(PluginContract.KEY_ERROR_CODE).orEmpty(),
            message = bundle.getString(PluginContract.KEY_ERROR_MESSAGE).orEmpty()
        )
    }.getOrNull()

    private fun action(value: PluginAction): Bundle = Bundle().apply {
        putString(PluginContract.KEY_ACTION_ID, value.id)
        putString(PluginContract.KEY_LABEL, value.label)
        putString(PluginContract.KEY_DESCRIPTION, value.description)
    }

    private fun parseAction(bundle: Bundle): PluginAction? = runCatching {
        PluginAction(
            id = bundle.requireString(PluginContract.KEY_ACTION_ID),
            label = bundle.requireString(PluginContract.KEY_LABEL),
            description = bundle.getString(PluginContract.KEY_DESCRIPTION).orEmpty()
        )
    }.getOrNull()

    private fun Bundle.requireString(key: String): String =
        requireNotNull(getString(key)).also { require(it.isNotBlank()) }
}
