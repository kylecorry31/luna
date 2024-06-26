package com.kylecorry.luna.equality

class HashChangeDetector: ChangeDetector {

    private val lock = Any()
    private var cachedHashes: MutableList<Int>? = null

    override fun hasChanges(values: Array<out Any?>): Boolean = synchronized(lock) {
        var changed = false

        val cached = if (cachedHashes == null) {
            cachedHashes = mutableListOf()
            changed = true
            cachedHashes
        } else {
            cachedHashes
        }!!

        if (cached.size != values.size) {
            values.forEachIndexed { index, value ->
                if (index >= cached.size) {
                    cached.add(value.hashCode())
                } else {
                    cached[index] = value.hashCode()
                }
            }
            changed = true
        } else {
            values.forEachIndexed { index, value ->
                val hash = value?.hashCode() ?: 0
                if (hash != cached[index]) {
                    cached[index] = hash
                    changed = true
                }
            }
        }

        return changed
    }

    override fun reset() = synchronized(lock) {
        cachedHashes = null
    }

}