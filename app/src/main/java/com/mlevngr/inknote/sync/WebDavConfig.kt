package com.mlevngr.inknote.sync

import java.net.URI

data class WebDavConfig(
    val internalUrl: String,
    val externalUrl: String,
    val remoteFolder: String,
    val username: String,
    val password: String
) {
    fun validated(): WebDavConfig {
        val internal = normalizeEndpoint(internalUrl, external = false)
        val external = normalizeEndpoint(externalUrl, external = true)
        require(internal.isNotEmpty() || external.isNotEmpty()) { "请至少填写一个 WebDAV 地址" }
        val folder = remoteFolder.trim().ifEmpty { DEFAULT_REMOTE_FOLDER }
        val segments = folder.split('/').map(String::trim).filter(String::isNotBlank)
        require(segments.isNotEmpty() && segments.none { it == "." || it == ".." || '\u0000' in it }) {
            "远端目录无效"
        }
        require(segments.all { it.length <= 80 }) { "远端目录名称过长" }
        return copy(
            internalUrl = internal,
            externalUrl = external,
            remoteFolder = segments.joinToString("/"),
            username = username.trim()
        )
    }

    fun endpoints(): List<WebDavEndpoint> = buildList {
        if (internalUrl.isNotBlank()) add(WebDavEndpoint(WebDavEndpoint.Kind.Internal, internalUrl))
        if (externalUrl.isNotBlank()) add(WebDavEndpoint(WebDavEndpoint.Kind.External, externalUrl))
    }

    val stateIdentity: String get() = "$internalUrl\n$externalUrl\n$username\n$remoteFolder"

    private fun normalizeEndpoint(value: String, external: Boolean): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val uri = runCatching { URI(trimmed) }.getOrElse { throw IllegalArgumentException("WebDAV 地址无效") }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || (!external && scheme == "http")) {
            if (external) "外网地址必须使用 HTTPS" else "内网地址必须使用 HTTP 或 HTTPS"
        }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.query == null) {
            "WebDAV 地址无效，请把账号密码填写在独立输入框中"
        }
        return trimmed.trimEnd('/') + "/"
    }

    companion object {
        const val DEFAULT_REMOTE_FOLDER = "Mote"
    }
}

data class WebDavEndpoint(val kind: Kind, val url: String) {
    enum class Kind { Internal, External }
}

data class WebDavConnectionResult(
    val endpoint: WebDavEndpoint,
    val available: Boolean,
    val message: String
)

data class WebDavSyncReport(
    val endpoint: WebDavEndpoint,
    val uploaded: Int,
    val downloaded: Int,
    val deletedLocal: Int,
    val deletedRemote: Int,
    val conflicts: Int
)
