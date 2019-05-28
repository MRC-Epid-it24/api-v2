package uk.ac.ncl.openlab.intake24.tools

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty


data class Test(val x: String, val y: Double)

fun main(args: Array<String>) {
    val server = embeddedServer(Netty, 8080) {

        routing {
            get("/") {
                call.respond(Test("хуй", 123.123))
            }
        }

        install(ContentNegotiation) {
            jackson {

            }
        }
    }

    server.start(wait = true)
}
