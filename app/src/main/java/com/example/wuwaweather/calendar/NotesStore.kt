package com.example.wuwaweather.calendar

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A calendar note or to-do, optionally with a reminder. */
data class Note(
    val id: Long,
    val date: String,          // ISO date, e.g. 2026-11-02
    val text: String,
    val isTodo: Boolean,
    val done: Boolean = false,
    val remindAt: Long? = null // epoch millis
)

object NotesStore {
    private const val PREFS = "velora_notes"
    private const val KEY = "notes"
    private val gson = Gson()
    private val _notes = MutableStateFlow<List<Note>?>(null)

    fun flow(context: Context): StateFlow<List<Note>?> { all(context); return _notes.asStateFlow() }

    fun all(context: Context): List<Note> {
        _notes.value?.let { return it }
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val list: List<Note> = json?.let {
            runCatching { gson.fromJson<List<Note>>(it, object : TypeToken<List<Note>>() {}.type) }.getOrNull()
        } ?: emptyList()
        _notes.value = list
        return list
    }

    fun upsert(context: Context, note: Note) = save(context, all(context).filterNot { it.id == note.id } + note)

    fun delete(context: Context, id: Long) = save(context, all(context).filterNot { it.id == id })

    private fun save(context: Context, list: List<Note>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, gson.toJson(list)).apply()
        _notes.value = list
    }
}
