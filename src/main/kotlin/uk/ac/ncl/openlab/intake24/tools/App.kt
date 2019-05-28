package uk.ac.ncl.openlab.intake24.tools

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jooq.SQLDialect
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient

data class FoodFrequencyRequest(val surveys: List<String>)

data class FoodFrequencyAcceptedResponse(val taskId: Int)

fun main() {

    val systemDatabase = DatabaseClient(
            "jdbc:postgresql://192.168.56.2:5432/intake24_foods",
            "intake24",
            "intake24", SQLDialect.POSTGRES_9_5)

    val taskStatusManager = TaskStatusManager()

    val foodFrequencyStatsService = FoodFrequencyStatsService(systemDatabase, taskStatusManager)

    val server = embeddedServer(Netty, 8080) {

        install(ContentNegotiation) {
            jackson()
        }

        install(Authentication) {
            val algorithm = Algorithm.HMAC256("zV;3:xvweW]@G5JTK7j;At<;pSj:NM=g[ALNpj?[NiWoUu3jK;K@s^a/LPf8S:5K")
            val jwtVerifier = JWT.require(algorithm).withIssuer("intake24").build()

            jwt("intake24") {
                verifier(jwtVerifier)

                authHeader { call ->
                    val token = call.request.header("X-Auth-Token")
                    if (token == null)
                        null
                    else
                        HttpAuthHeader.Single("Bearer", token)
                }

                validate { credential ->
                    JWTPrincipal(credential.payload)
                }
            }
        }

        routing {

            authenticate("intake24") {

                

                post("/food-frequency") {
                    val request = call.receiveOrNull(FoodFrequencyRequest::class)

                    val taskId = foodFrequencyStatsService.exportFoodFrequency(request?.surveys ?: emptyList())
                    call.respond(HttpStatusCode.Accepted, FoodFrequencyAcceptedResponse(taskId))
                }

                get("/tasks/{id}/status") {
                    val id = call.parameters["id"]?.toIntOrNull()

                    if (id == null)
                        call.respond(HttpStatusCode.BadRequest)
                    else {
                        val status = taskStatusManager.getTaskStatus(id)

                        if (status == null)
                            call.respond(HttpStatusCode.NotFound)
                        else
                            call.respond(status)
                    }
                }
            }


        }
    }

    server.start(wait = true)
}

