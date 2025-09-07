package com.kylecorry.luna.struct

import java.nio.charset.Charset

typealias struct = ByteArray

/**
 * Creates a struct with the given field sizes.
 * Limited to 255 fields.
 */
fun createStruct(vararg typeSizes: Int): struct {
    // First byte is the bytes per address
    // Second byte is the field count
    // Then address block (each points to the start of the field in as an index from the start)
    // Last block is the fields encoded as bytes
    val totalFieldSize = typeSizes.sum()
    val addressSize = totalFieldSize / 256 + 1
    val struct = ByteArray(2 + typeSizes.size * addressSize + totalFieldSize)
    struct[0] = addressSize.toByte()
    struct[1] = typeSizes.size.toByte()
    var offset = 0
    for (i in typeSizes.indices) {
        for (j in 0 until addressSize) {
            struct[2 + i * addressSize + j] = ((offset shr (8 * (addressSize - j - 1))) and 0xFF).toByte()
        }
        offset += typeSizes[i]
    }
    return struct
}

private fun struct.writeSingleByte(offset: Int, value: Byte) {
    this[offset] = value
}

private fun struct.writeBytes(offset: Int, bytes: ByteArray) {
    for (i in bytes.indices) {
        writeSingleByte(offset + i, bytes[i])
    }
}

fun struct.getOffset(index: Int): Int {
    val addressSize = this[0]
    val fieldCount = this[1]

    if (index !in 0..<fieldCount) {
        throw IndexOutOfBoundsException("Index $index out of bounds for struct with $fieldCount fields")
    }

    var offset = 0
    for (i in 0 until addressSize) {
        offset = (offset shl 8) or (this[2 + index * addressSize + i].toInt() and 0xFF)
    }
    return offset + 2 + fieldCount * addressSize
}

fun struct.getFieldSize(index: Int): Int {
    val fieldCount = this[1]
    val startOffset = getOffset(index)
    val endOffset = if (index == fieldCount - 1) {
        this.size
    } else {
        getOffset(index + 1)
    }
    return endOffset - startOffset
}

fun struct.writeByte(index: Int, value: Byte) {
    ensureValid(index, BYTE)
    writeSingleByte(getOffset(index), value)
}

fun struct.writeShort(index: Int, value: Short) {
    ensureValid(index, SHORT)
    val bits = SHORT * 8
    val start = bits - 8
    val offset = getOffset(index)
    for (i in 0 until SHORT) {
        writeSingleByte(offset + i, (value.toInt() shr (start - 8 * i)).toByte())
    }
}

fun struct.writeInt(index: Int, value: Int) {
    ensureValid(index, INT)
    val bits = INT * 8
    val start = bits - 8
    val offset = getOffset(index)
    for (i in 0..3) {
        writeSingleByte(offset + i, (value shr (start - 8 * i)).toByte())
    }
}

fun struct.writeLong(index: Int, value: Long) {
    ensureValid(index, LONG)
    val bits = LONG * 8
    val start = bits - 8
    val offset = getOffset(index)
    for (i in 0..7) {
        writeSingleByte(offset + i, (value shr (start - 8 * i)).toByte())
    }
}

fun struct.writeChar(index: Int, value: Char) {
    ensureValid(index, CHAR)
    val bits = CHAR * 8
    val start = bits - 8
    val offset = getOffset(index)
    for (i in 0..1) {
        writeSingleByte(offset + i, (value.code shr (start - 8 * i)).toByte())
    }
}

fun struct.writeFloat(index: Int, value: Float) {
    writeInt(index, value.toRawBits())
}

fun struct.writeDouble(index: Int, value: Double) {
    writeLong(index, value.toRawBits())
}

fun struct.writeBoolean(index: Int, value: Boolean) {
    writeByte(index, if (value) 1 else 0)
}

fun struct.writeByteArray(index: Int, bytes: ByteArray) {
    ensureValid(index, bytes.size)
    val offset = getOffset(index)
    val fieldSize = getFieldSize(index)
    writeBytes(offset, bytes)
    // Fill remaining bytes with 0s
    if (bytes.size < fieldSize) {
        fill(0, offset + bytes.size, offset + fieldSize)
    }
}

fun struct.writeString(index: Int, string: String, charset: Charset = Charsets.UTF_8) {
    writeByteArray(index, string.toByteArray(charset))
}

private fun struct.readSingleByte(offset: Int): Byte {
    return this[offset]
}

private fun struct.readBytes(offset: Int, length: Int): ByteArray {
    val bytes = ByteArray(length)
    for (i in 0 until length) {
        bytes[i] = readSingleByte(offset + i)
    }
    return bytes
}

fun struct.readByte(index: Int): Byte {
    ensureValid(index, BYTE)
    return readSingleByte(getOffset(index))
}

fun struct.readShort(index: Int): Short {
    ensureValid(index, SHORT)
    val bits = SHORT * 8
    val start = bits - 8
    val offset = getOffset(index)
    var value = 0
    for (i in 0..1) {
        val byte = readSingleByte(offset + i)
        value = value or ((byte.toInt() and 0xFF) shl (start - 8 * i))
    }
    return value.toShort()
}

fun struct.readInt(index: Int): Int {
    ensureValid(index, INT)
    val bits = INT * 8
    val start = bits - 8
    val offset = getOffset(index)
    var value = 0
    for (i in 0..3) {
        val byte = readSingleByte(offset + i)
        value = value or ((byte.toInt() and 0xFF) shl (start - 8 * i))
    }
    return value
}

fun struct.readLong(index: Int): Long {
    ensureValid(index, LONG)
    val bits = LONG * 8
    val start = bits - 8
    val offset = getOffset(index)
    var value = 0L
    for (i in 0..7) {
        val byte = readSingleByte(offset + i)
        value = value or ((byte.toLong() and 0xFF) shl (start - 8 * i))
    }
    return value
}

fun struct.readChar(index: Int): Char {
    ensureValid(index, CHAR)
    val bits = CHAR * 8
    val start = bits - 8
    val offset = getOffset(index)
    var value = 0
    for (i in 0..1) {
        val byte = readSingleByte(offset + i)
        value = value or ((byte.toInt() and 0xFF) shl (start - 8 * i))
    }
    return value.toChar()
}

fun struct.readFloat(index: Int): Float {
    return Float.fromBits(readInt(index))
}

fun struct.readDouble(index: Int): Double {
    return Double.fromBits(readLong(index))
}

fun struct.readBoolean(index: Int): Boolean {
    return readByte(index) != 0.toByte()
}

fun struct.readString(index: Int, charset: Charset = Charsets.UTF_8): String {
    val bytes = readByteArray(index)
    val nullTerminatorIndex = bytes.indexOf(0)
    if (nullTerminatorIndex != -1) {
        return bytes.copyOf(nullTerminatorIndex).toString(charset)
    }
    return bytes.toString(charset)
}

fun struct.readByteArray(index: Int): ByteArray {
    val fieldSize = getFieldSize(index)
    return readBytes(getOffset(index), fieldSize)
}

private fun struct.ensureValid(index: Int, byteCount: Int) {
    val fieldSize = getFieldSize(index)
    if (byteCount > fieldSize) {
        throw IllegalArgumentException("Field $index is not large enough to hold $byteCount bytes (max $fieldSize)")
    }
}

const val BYTE = 1
const val SHORT = 2
const val INT = 4
const val LONG = 8
const val CHAR = 2
const val FLOAT = 4
const val DOUBLE = 8
const val BOOLEAN = 1