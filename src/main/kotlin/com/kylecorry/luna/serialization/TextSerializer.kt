package com.kylecorry.luna.serialization

import com.kylecorry.luna.streams.readText
import com.kylecorry.luna.streams.write
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

class TextSerializer(private val charset: Charset = Charsets.UTF_8) : ISerializer<String> {
    override fun serialize(obj: String, stream: OutputStream) {
        try {
            stream.write(obj, charset)
        } catch (e: Exception) {
            throw SerializationException(e.message ?: "Unknown error", e)
        }
    }

    override fun deserialize(stream: InputStream): String {
        try {
            return stream.readText(charset)
        } catch (e: Exception) {
            throw DeserializationException(e.message ?: "Unknown error", e)
        }
    }
}