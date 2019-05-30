package uk.ac.ncl.intake24.storage

import com.google.inject.Inject
import com.google.inject.Singleton
import uk.ac.ncl.intake24.serialization.StringCodec
import java.time.Duration

@Singleton
class SharedStorageWithSerializer @Inject constructor(val sharedStorage: SharedStorage, val stringCodec: StringCodec) {

    fun <T> put(key: String, data: T, validFor: Duration): Unit {
        sharedStorage.put(key, stringCodec.encode(data), validFor)
    }

    fun remove(key: String): Unit {
        sharedStorage.remove(key)
    }

    inline fun <reified T : Any> get(key: String): T? {
        val data = sharedStorage.get(key)

        return if (data != null)
            stringCodec.decode(data, T::class)
        else
            null
    }
}