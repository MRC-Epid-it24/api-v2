package uk.ac.ncl.openlab.intake24.http4k

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.*
import com.auth0.jwt.interfaces.Payload
import com.google.inject.Inject
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory

object Intake24Roles {
    val superuser = "superuser"

    val globalSupport = "globalsupport"

    val surveyAdmin = "surveyadmin"

    val foodsAdmin = "foodsadmin"

    val imagesAdmin = "imagesadmin"

    val respondentSuffix = "/respondent"

    val staffSuffix = "/staff"

    val foodDatabaseMaintainerPrefix = "fdbm/"


    fun surveyStaff(surveyId: String) = "$surveyId$staffSuffix"

    fun surveySupport(surveyId: String) = "$surveyId/support"

    fun surveyRespondent(surveyId: String) = "$surveyId$respondentSuffix"

    fun foodDatabaseMaintainer(localeId: String) = "$foodDatabaseMaintainerPrefix$localeId"
}

data class Intake24User(val userId: Int, val roles: List<String>) {
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

    fun hasRole(role: String): Boolean {
        return roles.contains(role)
    }
}

typealias AuthenticatedHttpHandler = (Intake24User, Request) -> Response

class Intake24Authenticator(secret: String) {

    private val logger = LoggerFactory.getLogger(Intake24Authenticator::class.java)

    private val verifier = JWT.require(Algorithm.HMAC256(secret)).withIssuer("intake24").build()

    private val response401 = Response(Status.UNAUTHORIZED).header("WWW-Authenticate", "X-Auth-Token realm=\"Intake24\"")

    operator fun invoke(next: AuthenticatedHttpHandler): HttpHandler = { request ->

        val token = request.header("X-Auth-Token")

        if (token == null) {
            if (logger.isDebugEnabled) {
                logger.debug("X-Auth-Tokn header missing")
            }
            response401
        } else {
            try {
                val decodedJWT = verifier.verify(token)
                val user = Intake24User.fromJWTPayload(decodedJWT)

                next(user, request)
            } catch (e: JWTDecodeException) {
                if (logger.isDebugEnabled) {
                    logger.debug("JWT decode failed: ${e.message}")
                }
                response401
            } catch (e: SignatureVerificationException) {
                if (logger.isDebugEnabled) {
                    logger.debug("JWT signature verification failed: ${e.message}")
                }
                response401
            } catch (e: AlgorithmMismatchException) {
                if (logger.isDebugEnabled) {
                    logger.debug("JWT algorithm mismatch: ${e.message}")
                }
                response401
            } catch (e: TokenExpiredException) {
                if (logger.isDebugEnabled) {
                    logger.debug("JWT token expired: ${e.message}")
                }
                response401
            } catch (e: InvalidClaimException) {
                if (logger.isDebugEnabled) {
                    logger.debug("JWT invalid claim: ${e.message}")
                }
                response401
            }
        }
    }
}

class Security @Inject() constructor(private val authenticate: Intake24Authenticator) {

    fun allowAnyAuthenticated(requestHandler: AuthenticatedHttpHandler): HttpHandler = authenticate(requestHandler)

    fun check(isAllowed: (user: Intake24User, request: Request) -> Boolean, requestHandler: AuthenticatedHttpHandler): HttpHandler =
            authenticate { user, request ->
                if (user.hasRole(Intake24Roles.superuser) || isAllowed(user, request)) {
                    requestHandler(user, request)
                } else {
                    Response(Status.FORBIDDEN)
                }
            }

    fun allowAnyOf(roles: List<String>, requestHandler: AuthenticatedHttpHandler): HttpHandler =
            check({ user, _ -> roles.any { user.roles.contains(it) } }, requestHandler)

    fun allowFoodAdmins(requestHandler: AuthenticatedHttpHandler): HttpHandler =
            allowAnyOf(listOf(Intake24Roles.foodsAdmin), requestHandler)
}
