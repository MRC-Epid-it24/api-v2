package uk.ac.ncl.intake24.storage

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class SharedStorageSimpleImpl : SharedStorage {

    private data class Entry(val value: String, val expiresAt: Instant)

    private val map = ConcurrentHashMap<String, Entry>()

    override fun put(key: String, data: String, validFor: Duration): Unit {
        val expiresAt = Instant.now().plus(validFor)
        map[key] = Entry(data, expiresAt)
    }

    override fun remove(key: String): Unit {
        map.remove(key)
    }

    override fun get(key: String): String? {
        val entry = map.compute(key) { key, current ->
            if (current != null && Instant.now().isAfter(current.expiresAt))
                null
            else
                current
        }

        return entry?.value
    }
}