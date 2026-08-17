package com.mlevngr.inknote.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class SharedStoragePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val treeUri: Uri?
        get() = preferences.getString(TREE_URI, null)?.let(Uri::parse)

    fun save(treeUri: Uri) {
        preferences.edit { putString(TREE_URI, treeUri.toString()) }
    }

    fun clear() {
        preferences.edit { remove(TREE_URI) }
    }

    private companion object {
        const val PREFERENCES = "shared_note_storage"
        const val TREE_URI = "tree_uri"
    }
}
