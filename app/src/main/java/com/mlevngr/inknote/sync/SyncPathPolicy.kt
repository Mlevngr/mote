package com.mlevngr.inknote.sync

import java.io.File

object SyncPathPolicy {
    fun normalize(relativePath: String): String {
        val normalized = relativePath.replace('\\', '/').trim('/')
        require(normalized.isNotBlank()) { "同步路径不能为空" }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." || '\u0000' in it }) {
            "同步路径无效"
        }
        return segments.joinToString("/")
    }

    fun resolve(root: File, relativePath: String): File {
        val normalized = normalize(relativePath)
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, normalized).canonicalFile
        val prefix = "${canonicalRoot.path}${File.separator}"
        require(target.path.startsWith(prefix)) { "同步路径越过笔记目录" }
        return target
    }
}
