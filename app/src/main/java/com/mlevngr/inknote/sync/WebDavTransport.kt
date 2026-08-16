package com.mlevngr.inknote.sync

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import java.io.File
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class RemoteFile(
    val path: String,
    val version: String,
    val modifiedAt: Long,
    val size: Long
)

interface WebDavRemote {
    val endpoint: WebDavEndpoint
    fun ensureRoot()
    fun listFiles(): List<RemoteFile>
    fun download(path: String, destination: File)
    fun upload(path: String, source: File): RemoteFile
    fun delete(path: String)
}

class WebDavHttpException(
    val statusCode: Int,
    message: String,
    val retryable: Boolean
) : Exception(message)

class WebDavTransport(
    private val config: WebDavConfig,
    override val endpoint: WebDavEndpoint,
    private val client: OkHttpClient = defaultClient()
) : WebDavRemote {
    private val endpointRoot = endpoint.url.toHttpUrl()
    private val remoteFolderSegments = config.remoteFolder.split('/').filter(String::isNotBlank)
    private val rootUrl = collectionUrl(remoteFolderSegments)
    private val knownCollections = mutableSetOf<String>()

    fun testConnection() {
        propfind(endpointRoot, depth = 0).close()
    }

    override fun ensureRoot() {
        remoteFolderSegments.indices.forEach { index ->
            ensureCollection(remoteFolderSegments.take(index + 1))
        }
        knownCollections += ""
    }

    override fun listFiles(): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        val queue = ArrayDeque<Pair<HttpUrl, String>>()
        queue.add(rootUrl to "")
        while (queue.isNotEmpty()) {
            val (collection, prefix) = queue.removeFirst()
            propfind(collection, depth = 1).use { response ->
                val entries = WebDavXmlParser.parse(requireNotNull(response.body).byteStream())
                entries.forEach { parsed ->
                    val relative = relativePath(parsed.href) ?: return@forEach
                    if (relative.isBlank()) return@forEach
                    if (parsed.collection) {
                        if (knownCollections.add(relative)) {
                            queue.add(urlFor(relative, collection = true) to relative)
                        }
                    } else {
                        files += RemoteFile(
                            path = relative,
                            version = parsed.etag?.takeIf(String::isNotBlank)
                                ?: "${parsed.modifiedAt}:${parsed.size}",
                            modifiedAt = parsed.modifiedAt,
                            size = parsed.size
                        )
                    }
                }
            }
        }
        return files.distinctBy(RemoteFile::path)
    }

    override fun download(path: String, destination: File) {
        val normalized = SyncPathPolicy.normalize(path)
        val temporary = File(destination.parentFile, "${destination.name}.sync.tmp")
        temporary.parentFile?.mkdirs()
        val request = requestBuilder(urlFor(normalized, collection = false)).get().build()
        execute(request).use { response ->
            temporary.outputStream().use { output ->
                requireNotNull(response.body).byteStream().use { input -> input.copyTo(output) }
            }
        }
        destination.parentFile?.mkdirs()
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    override fun upload(path: String, source: File): RemoteFile {
        val normalized = SyncPathPolicy.normalize(path)
        ensureParentCollections(normalized)
        val request = requestBuilder(urlFor(normalized, collection = false))
            .put(source.asRequestBody(OCTET_STREAM))
            .build()
        execute(request).close()
        return stat(normalized)
    }

    override fun delete(path: String) {
        val request = requestBuilder(urlFor(SyncPathPolicy.normalize(path), collection = false))
            .delete()
            .build()
        execute(request).close()
    }

    private fun stat(path: String): RemoteFile {
        propfind(urlFor(path, collection = false), depth = 0).use { response ->
            val parsed = WebDavXmlParser.parse(requireNotNull(response.body).byteStream()).firstOrNull()
                ?: error("WebDAV 未返回文件状态")
            return RemoteFile(
                path = path,
                version = parsed.etag?.takeIf(String::isNotBlank)
                    ?: "${parsed.modifiedAt}:${parsed.size}",
                modifiedAt = parsed.modifiedAt,
                size = parsed.size
            )
        }
    }

    private fun ensureParentCollections(path: String) {
        val segments = path.split('/').dropLast(1)
        segments.indices.forEach { index ->
            val relative = segments.take(index + 1).joinToString("/")
            if (relative !in knownCollections) {
                ensureCollection(remoteFolderSegments + segments.take(index + 1))
                knownCollections += relative
            }
        }
    }

    private fun ensureCollection(segments: List<String>) {
        val url = collectionUrl(segments)
        val request = requestBuilder(url).method("MKCOL", null).build()
        client.newCall(request).execute().use { response ->
            if (response.code in setOf(200, 201, 204, 405)) return
            throw responseException(response)
        }
    }

    private fun propfind(url: HttpUrl, depth: Int): Response {
        val request = requestBuilder(url)
            .header("Depth", depth.toString())
            .header("Content-Type", "application/xml; charset=utf-8")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
            .build()
        return execute(request)
    }

    private fun execute(request: Request): Response {
        val response = client.newCall(request).execute()
        if (response.isSuccessful || response.code == 207) return response
        val exception = responseException(response)
        response.close()
        throw exception
    }

    private fun responseException(response: Response): WebDavHttpException {
        val retryable = response.code == 408 || response.code == 429 || response.code >= 500
        val message = when (response.code) {
            401, 403 -> "WebDAV 账号、密码或权限错误（HTTP ${response.code}）"
            404 -> "WebDAV 地址不存在（HTTP 404）"
            else -> "WebDAV 请求失败（HTTP ${response.code}）"
        }
        return WebDavHttpException(response.code, message, retryable)
    }

    private fun requestBuilder(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("User-Agent", "Mote-Android-WebDAV/1")
        .apply {
            if (config.username.isNotEmpty() || config.password.isNotEmpty()) {
                header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
            }
        }

    private fun collectionUrl(segments: List<String>): HttpUrl = endpointRoot.newBuilder().apply {
        segments.forEach(::addPathSegment)
        addPathSegment("")
    }.build()

    private fun urlFor(path: String, collection: Boolean): HttpUrl = rootUrl.newBuilder().apply {
        SyncPathPolicy.normalize(path).split('/').forEach(::addPathSegment)
        if (collection) addPathSegment("")
    }.build()

    private fun relativePath(href: String): String? {
        val resolved = rootUrl.resolve(href) ?: return null
        val rootSegments = rootUrl.pathSegments.dropLastWhile(String::isEmpty)
        val segments = resolved.pathSegments.dropLastWhile(String::isEmpty)
        if (segments.size < rootSegments.size || segments.take(rootSegments.size) != rootSegments) return null
        val relative = segments.drop(rootSegments.size).joinToString("/")
        return relative.takeIf(String::isNotBlank)?.let(SyncPathPolicy::normalize).orEmpty()
    }

    companion object {
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:resourcetype/><d:getetag/><d:getlastmodified/><d:getcontentlength/></d:prop>
            </d:propfind>"""

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }
}

internal data class ParsedWebDavResource(
    val href: String,
    val collection: Boolean,
    val etag: String?,
    val modifiedAt: Long,
    val size: Long
)

internal object WebDavXmlParser {
    fun parse(input: InputStream): List<ParsedWebDavResource> {
        val xml = BoundedInputStream(input, MAX_XML_BYTES).readBytes()
        require(!xml.toString(Charsets.ISO_8859_1).contains("<!DOCTYPE", ignoreCase = true)) {
            "WebDAV XML 不允许 DOCTYPE"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val responses = document.getElementsByTagNameNS("*", "response")
        return buildList {
            for (index in 0 until responses.length) {
                val response = responses.item(index) as? Element ?: continue
                val href = response.firstText("href") ?: continue
                add(
                    ParsedWebDavResource(
                        href = href,
                        collection = response.getElementsByTagNameNS("*", "collection").length > 0,
                        etag = response.firstText("getetag")?.trim(),
                        modifiedAt = response.firstText("getlastmodified")?.let(::parseHttpDate) ?: 0L,
                        size = response.firstText("getcontentlength")?.toLongOrNull() ?: 0L
                    )
                )
            }
        }
    }

    private fun Element.firstText(localName: String): String? =
        getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun parseHttpDate(value: String): Long = runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private const val MAX_XML_BYTES = 4L * 1024L * 1024L
}

private class BoundedInputStream(input: InputStream, private val maximum: Long) :
    FilterInputStream(input) {
    private var consumed = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) addConsumed(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) addConsumed(count.toLong())
        return count
    }

    private fun addConsumed(count: Long) {
        consumed += count
        if (consumed > maximum) throw IOException("WebDAV 目录响应过大")
    }
}
