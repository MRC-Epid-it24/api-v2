package uk.ac.ncl.openlab.intake24.http4k

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.*
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.tools.Intake24User

typealias AuthenticatedHttpHandler = (Intake24User, Request) -> Response

class Intake24AuthHandler(secret: String) {

    private val logger = LoggerFactory.getLogger(Intake24AuthHandler::class.java)

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

fun restrictToRoles(roles: List<String>, next: AuthenticatedHttpHandler): AuthenticatedHttpHandler =
        { user, request ->
            if (roles.any { user.roles.contains(it) }) {
                next(user, request)
            } else {
                Response(Status.FORBIDDEN)
            }
        }
