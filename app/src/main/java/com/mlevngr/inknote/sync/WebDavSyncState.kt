package com.mlevngr.inknote.sync

import java.io.File
import java.util.Base64
import java.util.Properties

data class WebDavSyncRecord(val localHash: String, val remoteVersion: String)

class WebDavSyncState(private val file: File) {
    fun load(identity: String): Map<String, WebDavSyncRecord> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            val properties = Properties().apply { file.inputStream().use(::load) }
            if (properties.getProperty(IDENTITY) != identity) return emptyMap()
            properties.stringPropertyNames()
                .asSequence()
                .filter { it.startsWith(PATH_PREFIX) }
                .associate { key ->
                    val path = String(
                        Base64.getUrlDecoder().decode(key.removePrefix(PATH_PREFIX)),
                        Charsets.UTF_8
                    )
                    val value = requireNotNull(properties.getProperty(key)).split('\t', limit = 2)
                    path to WebDavSyncRecord(value[0], value.getOrElse(1) { "" })
                }
        }.getOrDefault(emptyMap())
    }

    fun save(identity: String, records: Map<String, WebDavSyncRecord>) {
        val properties = Properties().apply {
            setProperty(IDENTITY, identity)
            records.toSortedMap().forEach { (path, record) ->
                val encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(path.toByteArray())
                setProperty("$PATH_PREFIX$encoded", "${record.localHash}\t${record.remoteVersion}")
            }
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().use { properties.store(it, null) }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private companion object {
        const val IDENTITY = "identity"
        const val PATH_PREFIX = "path."
    }
}
