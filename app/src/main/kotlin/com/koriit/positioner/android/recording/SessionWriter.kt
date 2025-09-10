package com.koriit.positioner.android.recording

import android.content.Context
import android.net.Uri
import java.io.Closeable
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stream session rotations into a compressed file.
 */
class SessionWriter private constructor(private val out: OutputStream) : Closeable {
    private val gzip = GZIPOutputStream(out)
    private val writer = gzip.bufferedWriter()

    /**
     * Append a rotation to the session file.
     */
    fun write(rotation: Rotation) {
        val json = Json.encodeToString(rotation)
        writer.write(json)
        writer.newLine()
    }

    override fun close() {
        writer.flush()
        gzip.finish()
        gzip.close()
    }

    companion object {
        fun open(context: Context, uri: Uri): SessionWriter? {
            val stream = context.contentResolver.openOutputStream(uri) ?: return null
            return SessionWriter(stream)
        }
    }
}
