package com.kylecorry.luna.struct

typealias struct = Array<ByteArray>

/**
 * Create a struct with the given type sizes
 * Limited to 256 entries
 */
fun createStruct(vararg typeSizes: Int): struct {
    // First array maps index to offset, second array holds the values
    val offsets = ByteArray(typeSizes.size)
    for (i in 1 until typeSizes.size) {
        offsets[i] = (offsets[i - 1] + typeSizes[i - 1]).toByte()
    }
    return arrayOf(offsets, ByteArray(typeSizes.sum()))
}

fun struct.writeSingleByte(offset: Int, value: Byte) {
    this[1][offset] = value
}

fun struct.writeBytes(offset: Int, bytes: ByteArray) {
    for (i in bytes.indices) {
        writeSingleByte(offset + i, bytes[i])
    }
}

fun struct.getOffset(index: Int): Int {
    return this[0][index].toInt()
}

fun struct.writeByte(index: Int, value: Byte) {
    writeSingleByte(getOffset(index), value)
}

fun struct.writeShort(index: Int, value: Short) {
    val bits = SHORT * 8
    val start = bits - 8
    val offset = getOffset(index)
    for (i in 0 until SHORT) {
        writeSingleByte(offset + i, (value.toInt() shr (start - 8 * i)).toByte())
    }
}

fun struct.writeInt(index: Int, value: Int) {
    val bits = INT * 8
    val start = bits - 8
    val offset = getOffset(index)
    for (i in 0..3) {
        writeSingleByte(offset + i, (value shr (start - 8 * i)).toByte())
    }
}

fun struct.writeLong(index: Int, value: Long) {
    val bits = LONG * 8
    val start = bits - 8
    val offset = getOffset(index)
    for (i in 0..7) {
        writeSingleByte(offset + i, (value shr (start - 8 * i)).toByte())
    }
}

fun struct.writeChar(index: Int, value: Char) {
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

fun struct.readSingleByte(offset: Int): Byte {
    return this[1][offset]
}

fun struct.readBytes(offset: Int, length: Int): ByteArray {
    val bytes = ByteArray(length)
    for (i in 0 until length) {
        bytes[i] = readSingleByte(offset + i)
    }
    return bytes
}

fun struct.readByte(index: Int): Byte {
    return readSingleByte(getOffset(index))
}

fun struct.readShort(index: Int): Short {
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

const val BYTE = 1
const val SHORT = 2
const val INT = 4
const val LONG = 8
const val CHAR = 2
const val FLOAT = 4
const val DOUBLE = 8
const val BOOLEAN = 1