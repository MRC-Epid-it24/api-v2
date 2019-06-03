package uk.ac.ncl.intake24.serialization

import com.google.inject.AbstractModule

class JacksonCodecModule : AbstractModule() {

    override fun configure() {
        bind(StringCodec::class.java).to(JacksonCodec::class.java)
    }
}