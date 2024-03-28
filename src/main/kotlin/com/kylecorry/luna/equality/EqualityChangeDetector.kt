package com.kylecorry.luna.equality

class EqualityChangeDetector : ChangeDetector {

    private val lock = Any()
    private var cachedValues: MutableList<Any?>? = null

    override fun hasChanges(values: Array<out Any?>): Boolean = synchronized(lock) {
        var changed = false

        val cached = if (cachedValues == null) {
            cachedValues = mutableListOf()
            changed = true
            cachedValues
        } else {
            cachedValues
        }!!

        if (cached.size != values.size) {
            values.forEachIndexed { index, value ->
                if (index >= cached.size) {
                    cached.add(value)
                } else {
                    cached[index] = value
                }
            }
            changed = true
        } else {
            values.forEachIndexed { index, value ->
                if (value != cached[index]) {
                    cached[index] = value
                    changed = true
                }
            }
        }

        return changed
    }

    override fun reset() = synchronized(lock) {
        cachedValues = null
    }

}