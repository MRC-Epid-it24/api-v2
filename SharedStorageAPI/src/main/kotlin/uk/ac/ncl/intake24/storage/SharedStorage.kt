package uk.ac.ncl.intake24.storage

import java.time.Duration

interface SharedStorage {
    fun put(key: String, data: String, validFor: Duration): Unit
    fun remove(key: String): Unit
    fun get(key: String): String?
}