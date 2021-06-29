package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.tools.uksab.UKSAUpdatePsmService

class UKSAUpdatePsmController @Inject constructor(
    private val service: UKSAUpdatePsmService
) {

    private val logger = LoggerFactory.getLogger(UKSAUpdatePsmController::class.java)

    private data class ErrorsResponse(val errors: List<String>)

    fun updatePortionSizes(user: Intake24User, request: Request): Response {
        service.updatePortionSizes()
        return Response(Status.OK)
    }
}
