package uk.ac.ncl.openlab.intake24.tools

import com.auth0.jwt.interfaces.Payload
import io.ktor.auth.Principal


data class Intake24User(val userId: Int, val roles: List<String>) : Principal {
    companion object {
        fun fromJWTPayload(payload: Payload): Intake24User {

            val userId = payload.claims["userId"]!!.asInt()
            val jwtRoles = payload.claims["roles"]!!.asList(java.lang.String::class.java)

            val ktRoles = jwtRoles.fold(emptyList<String>()) { acc, next ->
                acc.plus(next as String)
            }

            return Intake24User(userId, ktRoles)
        }
    }
}
