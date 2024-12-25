package com.kylecorry.luna.struct

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StructTest {

    @Test
    fun testByte() {
        val struct = createStruct(BYTE)
        struct.writeByte(0, 0x7F.toByte())
        assertEquals(0x7F.toByte(), struct.readByte(0))
    }

    @Test
    fun testShort() {
        val struct = createStruct(SHORT)
        struct.writeShort(0, 0x7FFF.toShort())
        assertEquals(0x7FFF.toShort(), struct.readShort(0))
    }

    @Test
    fun testInt() {
        val struct = createStruct(INT)
        struct.writeInt(0, 0x7FFFFFFF)
        assertEquals(0x7FFFFFFF, struct.readInt(0))
    }

    @Test
    fun testLong() {
        val struct = createStruct(LONG)
        struct.writeLong(0, 0x7FFFFFFFFFFFFFFF)
        assertEquals(0x7FFFFFFFFFFFFFFF, struct.readLong(0))
    }

    @Test
    fun testChar() {
        val struct = createStruct(CHAR)
        struct.writeChar(0, 'a')
        assertEquals('a', struct.readChar(0))
    }

    @Test
    fun testFloat() {
        val struct = createStruct(FLOAT)
        struct.writeFloat(0, 1.234f)
        assertEquals(1.234f, struct.readFloat(0))
    }

    @Test
    fun testDouble() {
        val struct = createStruct(DOUBLE)
        struct.writeDouble(0, 1.234)
        assertEquals(1.234, struct.readDouble(0))
    }

    @Test
    fun testBoolean() {
        val struct = createStruct(BOOLEAN)
        struct.writeBoolean(0, true)
        assertTrue(struct.readBoolean(0))
    }

    @Test
    fun testMultiple() {
        val struct = createStruct(BYTE, INT, LONG, CHAR, FLOAT, DOUBLE, BOOLEAN)
        struct.writeByte(0, 0x7F.toByte())
        struct.writeInt(1, 0x7FFFFFFF)
        struct.writeLong(2, 0x7FFFFFFFFFFFFFFF)
        struct.writeChar(3, 'a')
        struct.writeFloat(4, 1.234f)
        struct.writeDouble(5, 1.234)
        struct.writeBoolean(6, true)

        assertEquals(0x7F.toByte(), struct.readSingleByte(0))
        assertEquals(0x7FFFFFFF, struct.readInt(1))
        assertEquals(0x7FFFFFFFFFFFFFFF, struct.readLong(2))
        assertEquals('a', struct.readChar(3))
        assertEquals(1.234f, struct.readFloat(4))
        assertEquals(1.234, struct.readDouble(5))
        assertTrue(struct.readBoolean(6))
    }

    @Test
    fun testBytes() {
        val struct = createStruct(4)
        struct.writeBytes(0, byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F))
        assertArrayEquals(byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F), struct.readBytes(0, 4))
    }

    @Test
    fun outOfBounds() {
        val struct = createStruct(BYTE)
        assertThrows(IndexOutOfBoundsException::class.java) {
            struct.writeByte(1, 0x7F.toByte())
        }
        assertThrows(IndexOutOfBoundsException::class.java) {
            struct.readByte(1)
        }
    }

}