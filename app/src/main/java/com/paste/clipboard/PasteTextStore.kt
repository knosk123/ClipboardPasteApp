package com.paste.clipboard

import android.content.Context

class PasteTextStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "paste_text_store"
        private const val KEY_TEXT = "pending_text"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(text: String) {
        prefs.edit().putString(KEY_TEXT, text).apply()
    }

    fun get(): String {
        return prefs.getString(KEY_TEXT, "").orEmpty()
    }

    fun clear() {
        prefs.edit().remove(KEY_TEXT).apply()
    }
}
