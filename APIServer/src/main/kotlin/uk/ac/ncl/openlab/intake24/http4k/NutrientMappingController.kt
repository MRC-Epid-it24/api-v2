package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import uk.ac.ncl.openlab.intake24.services.NutrientMappingService

class NutrientMappingController @Inject constructor(private val nutrientMappingService: NutrientMappingService) {

    fun recalculateNutrients(user: Intake24User, request: Request): Response {
        val surveyId = request.path("surveyId")

        if (surveyId == null)
            return Response(Status.BAD_REQUEST)
        else {
            val taskId = nutrientMappingService.recalculateNutrients(user.userId, surveyId)
            return Response(Status.ACCEPTED).body(taskId.toString())
        }
    }
}
