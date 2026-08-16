package com.mlevngr.inknote.plugins

import com.mlevngr.mote.plugin.api.PluginCapability

object PluginCapabilityLabels {
    fun label(capability: PluginCapability): String = when (capability) {
        PluginCapability.READ_SELECTION -> "读取当前选择"
        PluginCapability.READ_FULL_NOTE -> "读取整篇笔记"
        PluginCapability.MODIFY_TEXT -> "修改正文"
        PluginCapability.MODIFY_STRUCTURE -> "调整标题、列表和文档结构"
        PluginCapability.READ_ATTACHMENTS -> "读取附件内容"
        PluginCapability.CREATE_ATTACHMENTS -> "创建附件"
        PluginCapability.NETWORK_ACCESS -> "访问网络"
        PluginCapability.VOICE_INPUT -> "使用语音输入"
        PluginCapability.EXPORT_NOTES -> "导出笔记"
        PluginCapability.SYNC_PROVIDER -> "提供同步服务"
    }
}
