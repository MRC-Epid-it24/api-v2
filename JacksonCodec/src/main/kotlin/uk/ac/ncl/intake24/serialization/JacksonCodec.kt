package uk.ac.ncl.intake24.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class JacksonCodec : StringCodec {
    private val mapper = ObjectMapper()

    init {
        mapper
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    }

    override fun <T> encode(obj: T): String {
        return mapper.writeValueAsString(obj)
    }

    override fun <T : Any> decode(string: String, klass: KClass<T>): T {
        return mapper.readValue(string, klass.java)
    }
}