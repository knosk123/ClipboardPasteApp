package com.paste.clipboard

import android.content.Context
import org.json.JSONArray

class HistoryManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "paste_history"
        private const val KEY_HISTORY = "history_list"
        private const val MAX_HISTORY = 20
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addEntry(text: String) {
        val list = getAll().toMutableList()
        list.remove(text)
        list.add(0, text)
        if (list.size > MAX_HISTORY) {
            list.removeAt(list.lastIndex)
        }
        save(list)
    }

    fun getAll(): List<String> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(list: List<String>) {
        val arr = JSONArray(list)
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
