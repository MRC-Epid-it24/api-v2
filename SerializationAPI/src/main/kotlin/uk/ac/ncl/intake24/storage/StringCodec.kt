package uk.ac.ncl.intake24.serialization

import kotlin.reflect.KClass

interface StringCodec {
    fun <T> encode(obj: T): String
    fun <T : Any> decode(string: String, klass: KClass<T>): T
}