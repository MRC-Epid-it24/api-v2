package uk.ac.ncl.openlab.intake24.http4k

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Inject
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.tools.FoodFrequencyStatsService

class FoodFrequencyStatsController @Inject constructor(private val foodFrequencyStatsService: FoodFrequencyStatsService,
                                                       private val stringCodec: StringCodec) {

    private data class Req(@JsonProperty("locale") val locale: String,
                           @JsonProperty("limitToSurveys") val limitToSurveys: List<String>?)

    private val logger = LoggerFactory.getLogger(FoodFrequencyStatsController::class.java)

    fun exportFrequencies(user: Intake24User, request: Request): Response {

        try {
            val params = stringCodec.decode(request.bodyString(), Req::class)
            val id = foodFrequencyStatsService.exportFoodFrequency(user.userId, params.locale, params.limitToSurveys ?: emptyList())
            return Response(Status.ACCEPTED).body(id.toString())
        } catch (e: Exception) {
            logger.debug("Failed to decode body", e)
            return Response(Status.BAD_REQUEST).body(e.message ?: "")
        }
    }
}