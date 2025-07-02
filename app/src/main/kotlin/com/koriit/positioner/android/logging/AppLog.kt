package com.koriit.positioner.android.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Simple in-memory log store that also forwards messages to Android's Logcat.
 * Debug level is used by default.
 */
object AppLog {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    fun d(tag: String, msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.d(tag, msg, throwable)
        } else {
            Log.d(tag, msg)
        }
        add("$tag: $msg")
    }

    private fun add(entry: String) {
        _logs.value = _logs.value + entry
    }
}
