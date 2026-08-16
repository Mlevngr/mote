package com.mlevngr.mote.plugin.api

object PluginContract {
    const val API_VERSION = 1
    const val SERVICE_ACTION = "com.mlevngr.mote.action.PLUGIN_SERVICE"

    const val META_API_VERSION = "com.mlevngr.mote.plugin.API_VERSION"
    const val META_PLUGIN_ID = "com.mlevngr.mote.plugin.ID"
    const val META_PLUGIN_LABEL = "com.mlevngr.mote.plugin.LABEL"

    const val KEY_API_VERSION = "api_version"
    const val KEY_PLUGIN_ID = "plugin_id"
    const val KEY_LABEL = "label"
    const val KEY_DESCRIPTION = "description"
    const val KEY_CAPABILITIES = "capabilities"
    const val KEY_ACTIONS = "actions"
    const val KEY_ACTION_ID = "action_id"
    const val KEY_SETTINGS_ACTIVITY = "settings_activity"

    const val KEY_SESSION_ID = "session_id"
    const val KEY_REQUEST_ID = "request_id"
    const val KEY_BASE_REVISION = "base_revision"
    const val KEY_NOTE_TITLE = "note_title"
    const val KEY_MARKDOWN = "markdown"
    const val KEY_SELECTION_START = "selection_start"
    const val KEY_SELECTION_END = "selection_end"
    const val KEY_RESULT_SUMMARY = "result_summary"
    const val KEY_ERROR_CODE = "error_code"
    const val KEY_ERROR_MESSAGE = "error_message"

    const val ERROR_INVALID_REQUEST = "invalid_request"
    const val ERROR_NOT_CONFIGURED = "not_configured"
    const val ERROR_NETWORK = "network"
    const val ERROR_CANCELLED = "cancelled"
    const val ERROR_PLUGIN_FAILURE = "plugin_failure"
}

enum class PluginCapability {
    READ_SELECTION,
    READ_FULL_NOTE,
    MODIFY_TEXT,
    MODIFY_STRUCTURE,
    READ_ATTACHMENTS,
    CREATE_ATTACHMENTS,
    NETWORK_ACCESS,
    VOICE_INPUT,
    EXPORT_NOTES,
    SYNC_PROVIDER
}

data class PluginAction(
    val id: String,
    val label: String,
    val description: String
)

data class PluginDescriptor(
    val apiVersion: Int,
    val pluginId: String,
    val label: String,
    val description: String,
    val capabilities: Set<PluginCapability>,
    val actions: List<PluginAction>,
    val settingsActivity: String? = null
) {
    fun isCompatible(hostApiVersion: Int = PluginContract.API_VERSION): Boolean =
        apiVersion in 1..hostApiVersion && pluginId.isNotBlank() && actions.isNotEmpty()

    fun approvalKey(packageName: String): String = buildString {
        append(packageName)
        append('|')
        append(pluginId)
        append('|')
        append(apiVersion)
        append('|')
        append(capabilities.map(Enum<*>::name).sorted().joinToString(","))
    }
}

data class PluginRequest(
    val sessionId: String,
    val requestId: String,
    val actionId: String,
    val baseRevision: String,
    val noteTitle: String,
    val markdown: String,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1
)

data class PluginResult(
    val sessionId: String,
    val requestId: String,
    val baseRevision: String,
    val markdown: String,
    val summary: String
)

data class PluginError(
    val sessionId: String,
    val requestId: String,
    val code: String,
    val message: String
)
