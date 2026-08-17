package com.mlevngr.inknote.storage

object DocumentTreePolicy {
    fun isSameOrDescendant(
        candidateAuthority: String?,
        candidateDocumentId: String,
        ancestorAuthority: String?,
        ancestorDocumentId: String
    ): Boolean {
        if (candidateAuthority != ancestorAuthority) return false
        val candidate = candidateDocumentId.trimEnd('/')
        val ancestor = ancestorDocumentId.trimEnd('/')
        return candidate == ancestor || candidate.startsWith("$ancestor/")
    }
}
