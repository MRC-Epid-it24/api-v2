package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import uk.ac.ncl.openlab.intake24.services.NutrientMappingExportService
import uk.ac.ncl.openlab.intake24.services.NutrientMappingService

class NutrientMappingController @Inject constructor(private val nutrientMappingService: NutrientMappingService,
                                                    private val nutrientMappingExportService: NutrientMappingExportService) {

    fun recalculateNutrients(user: Intake24User, request: Request): Response {
        val surveyId = request.path("surveyId")

        if (surveyId == null)
            return Response(Status.BAD_REQUEST)
        else {
            val taskId = nutrientMappingService.recalculateNutrients(user.userId, surveyId)
            return Response(Status.ACCEPTED).body(taskId.toString())
        }
    }

    fun exportMapping(user: Intake24User, request: Request): Response {
        val localeId = request.path("localeId")
        val insertBOM = request.query("forceBOM") != null

        if (localeId == null)
            return Response(Status.BAD_REQUEST)
        else {
            val taskId = nutrientMappingExportService.exportMapping(user.userId, localeId, insertBOM)
            return Response(Status.ACCEPTED).body(taskId.toString())
        }
    }
}
