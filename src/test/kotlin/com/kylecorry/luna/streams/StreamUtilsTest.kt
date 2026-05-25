package com.kylecorry.luna.streams

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader

class StreamUtilsTest {

    @Test
    fun testInputStreamReadUntilChar() {
        val input = ByteArrayInputStream("hello,world".toByteArray())

        val output = input.readUntil(',')

        assertEquals("hello", output)
    }

    @Test
    fun testReadBytesUntilReturnsBytesBeforeStopSequence() {
        val input = ByteArrayInputStream("abc<END>def".toByteArray())

        val output = input.readBytesUntil("<END>".toByteArray())

        assertArrayEquals("abc".toByteArray(), output)
    }

    @Test
    fun testReadBytesUntilSaveContentFalse() {
        val input = ByteArrayInputStream("abc<END>def".toByteArray())

        val output = input.readBytesUntil("<END>".toByteArray(), saveContent = false)

        assertArrayEquals(ByteArray(0), output)
        assertEquals("def", input.readText())
    }

    @Test
    fun testReadBytesUntilTrimLines() {
        val input = ByteArrayInputStream("a\nb\rc<END>".toByteArray())

        val output = input.readBytesUntil("<END>".toByteArray())

        assertArrayEquals("abc".toByteArray(), output)
    }

    @Test
    fun testReadBytesUntilTrimStartAndEndOnly() {
        val input = ByteArrayInputStream("\nA\nB\r\n<END>".toByteArray())

        val output = input.readBytesUntil(
            "<END>".toByteArray(),
            trimLines = true,
            trimStartAndEndOnly = true
        )

        assertArrayEquals("A\nB".toByteArray(), output)
    }

    @Test
    fun testReadLinesUntil() {
        val input = BufferedReader(StringReader(" one \n twoEND \n three"))

        val output = input.readLinesUntil("END")

        assertEquals("onetwo", output)
    }

    @Test
    fun testReadLinesUntilSaveContentFalse() {
        val input = BufferedReader(StringReader("one\ntwoEND\nthree"))

        val output = input.readLinesUntil("END", saveContent = false)

        assertEquals("", output)
    }

    @Test
    fun testBufferedReaderReadUntil() {
        val input = BufferedReader(StringReader("ab\ncd<end>ef"))

        val output = input.readUntil("<end>")

        assertEquals("abcd", output)
    }

    @Test
    fun testForEachCharIteratesAllChars() {
        val input = BufferedReader(StringReader("abc"))
        val chars = mutableListOf<Char>()

        input.forEachChar { chars.add(it) }

        assertEquals(listOf('a', 'b', 'c'), chars)
    }

    @Test
    fun testForEachByteIteratesAllBytes() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val bytes = mutableListOf<Int>()

        input.forEachByte { bytes.add(it) }

        assertEquals(listOf(1, 2, 3), bytes)
    }

    @Test
    fun testInputStreamReadUntilPredicate() {
        val input = ByteArrayInputStream("abc123".toByteArray())

        val output = input.readUntil { it.isDigit() }

        assertEquals("abc", output)
    }

    @Test
    fun testInputStreamReadLine() {
        val input = ByteArrayInputStream("line1\nline2".toByteArray())

        val output = input.readLine()

        assertEquals("line1", output)
    }

    @Test
    fun testInputStreamReadTextDefaultCharset() {
        val input = ByteArrayInputStream("hello".toByteArray())

        val output = input.readText()

        assertEquals("hello", output)
    }

    @Test
    fun testBufferedReaderReadUntilSaveContentFalse() {
        val input = BufferedReader(StringReader("ab<end>cd"))

        val output = input.readUntil("<end>", saveContent = false)

        assertEquals("", output)
    }

    @Test
    fun testBufferedReaderReadUntilDoesNotTrimNewlinesWhenDisabled() {
        val input = BufferedReader(StringReader("a\nb<end>"))

        val output = input.readUntil("<end>", trimLines = false)

        assertEquals("a\nb", output)
    }

    @Test
    fun testReadLinesUntilDoesNotTrimLinesWhenDisabled() {
        val input = BufferedReader(StringReader(" one \n twoEND"))

        val output = input.readLinesUntil("END", trimLines = false)

        assertEquals(" one  two", output)
    }

    @Test
    fun testOutputStreamWriteString() {
        val output = ByteArrayOutputStream()

        output.write("hello")

        assertArrayEquals("hello".toByteArray(), output.toByteArray())
    }

    @Test
    fun testOutputStreamWriteAll() {
        val output = ByteArrayOutputStream()

        output.writeAll(byteArrayOf(1, 2, 3))

        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test
    fun testOutputStreamWriteByte() {
        val output = ByteArrayOutputStream()

        output.write(0x41.toByte())

        assertArrayEquals(byteArrayOf(0x41), output.toByteArray())
    }

}
