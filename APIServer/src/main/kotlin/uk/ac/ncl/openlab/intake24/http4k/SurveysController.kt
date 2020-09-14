package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.services.SurveysService

data class SubmissionsCountResponse(val count: Int)

class SurveysController @Inject constructor(private val surveysService: SurveysService, private val codec: StringCodec) {

    fun getSubmissionsCount(user: Intake24User, request: Request): Response {
        val surveyId = request.path("surveyId")

        if (surveyId == null)
            return Response(Status.BAD_REQUEST)
        else {
            val count = surveysService.getSubmissionsCount(surveyId)

            if (count == null)
                return Response(Status.NOT_FOUND)
            else
                return Response(Status.OK).body(codec.encode(SubmissionsCountResponse(count)))
        }
    }
}
