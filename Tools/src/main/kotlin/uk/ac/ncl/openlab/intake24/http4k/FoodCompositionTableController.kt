package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import org.slf4j.LoggerFactory
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.tools.FoodCompositionTable
import uk.ac.ncl.openlab.intake24.tools.FoodCompositionTableService

class FoodCompositionTableController @Inject constructor(
        private val fctService: FoodCompositionTableService,
        private val stringCodec: StringCodec) {

    private val logger = LoggerFactory.getLogger(FoodCompositionTableController::class.java)

    fun getCompositionTables(user: Intake24User, request: Request): Response {
        return Response(Status.OK)
                .body(stringCodec.encode(fctService.getFoodCompositionTables()))
                .header("Content-Type", "application/json")
    }

    fun getCompositionTable(user: Intake24User, request: Request): Response {
        val tableId = request.path("tableId")

        return if (tableId == null)
            Response(Status.BAD_REQUEST)
        else {
            translateDatabaseErrors(logger) {
                Response(Status.OK)
                        .body(stringCodec.encode(fctService.getFoodCompositionTable(tableId)))
                        .header("Content-Type", "application/json")
            }
        }
    }

    fun updateCompositionTable(user: Intake24User, request: Request): Response {
        val tableId = request.path("tableId")

        if (tableId == null)
            return Response(Status.BAD_REQUEST)
        else {


                val update = stringCodec.decode(request.bodyString(), FoodCompositionTable::class)

                if (update == null) {
                    return Response(Status.BAD_REQUEST)
                } else {
                    fctService.updateFoodCompositionTable(tableId, update)

                    return Response(Status.OK)
                            .header("Content-Type", "application/json")
                }
        }
    }

    fun getNutrientTypes(user: Intake24User, request: Request): Response {
        return Response(Status.OK)
                .body(stringCodec.encode(fctService.getNutrientTypes()))
                .header("Content-Type", "application/json")
    }

}