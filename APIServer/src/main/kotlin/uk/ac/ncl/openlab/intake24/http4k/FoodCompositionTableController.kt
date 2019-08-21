package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import org.slf4j.LoggerFactory
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.tools.*
import uk.ac.ncl.openlab.intake24.tools.csvutils.offsetToExcelColumn
import java.io.FileReader
import java.io.InputStreamReader
import java.io.StringWriter

class FoodCompositionTableController @Inject constructor(
        private val fctService: FoodCompositionTableService,
        private val stringCodec: StringCodec,
        private val errorUtils: ErrorUtils) {

    private val logger = LoggerFactory.getLogger(FoodCompositionTableController::class.java)

    fun getCompositionTables(user: Intake24User, request: Request): Response {
        return Response(Status.OK)
                .body(stringCodec.encode(fctService.getFoodCompositionTables()))
    }

    fun getCompositionTable(user: Intake24User, request: Request): Response {
        val tableId = request.path("tableId")

        return if (tableId == null)
            Response(Status.BAD_REQUEST)
        else {
            Response(Status.OK)
                    .body(stringCodec.encode(fctService.getFoodCompositionTable(tableId)))
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
            }
        }
    }

    fun createCompositionTable(user: Intake24User, request: Request): Response {
        val newTable = stringCodec.decode(request.bodyString(), FoodCompositionTable::class)
        fctService.createFoodCompositionTable(newTable)
        return Response(Status.OK)
    }

    fun uploadCsv(user: Intake24User, request: Request): Response {
        val tableId = request.path("tableId")

        if (tableId == null)
            return Response(Status.BAD_REQUEST)
        else {
            val form = MultipartFormBody.from(request)
            val file = form.file("file")

            return if (file != null) {
                try {
                    val tableInfo = fctService.getFoodCompositionTable(tableId)

                    val nutrientTypeDescriptions = fctService.getNutrientTypes().fold(emptyMap<Int, String>()) { acc, nutrient ->
                        acc + Pair(nutrient.id, nutrient.name)
                    }

                    val parseResult = FoodCompositionCsvParser.parseTable(file.content, tableInfo.mapping, nutrientTypeDescriptions)
                    fctService.updateNutrientRecords(tableId, parseResult.rows)

                    Response(Status.OK).body(stringCodec.encode(parseResult.warnings))
                } catch (e: CsvParseException) {
                    errorUtils.errorResponse(Status.BAD_REQUEST, e)
                }
            } else {
                errorUtils.errorResponse(Status.BAD_REQUEST, "Missing file part")
            }
        }
    }

    fun getNutrientTypes(user: Intake24User, request: Request): Response {
        return Response(Status.OK)
                .body(stringCodec.encode(fctService.getNutrientTypes()))
    }

    fun getMappingCsv(user: Intake24User, request: Request): Response {

        val tableId = request.path("tableId")

        if (tableId == null)
            return Response(Status.BAD_REQUEST)
        else {

            val nutrientTypes = fctService.getNutrientTypes().associateBy { it.id }
            val mapping = fctService.getFoodCompositionTable(tableId).mapping


            val bodyWriter = StringWriter()
            val csvWriter = CSVWriter(bodyWriter)

            csvWriter.writeNext(arrayOf("Source column", "Description", "Unit", "Intake24 nutrient ID"))

            mapping.nutrientColumns.sortedBy { it.columnOffset }.forEach { column ->
                val nutrient = nutrientTypes[column.nutrientId]
                        ?: throw IllegalArgumentException("Unexpected nutrient ID: ${column.nutrientId}")
                csvWriter.writeNext(arrayOf(offsetToExcelColumn(column.columnOffset), nutrient.name, nutrient.unit, column.nutrientId.toString()))
            }

            csvWriter.close()

            return Response(Status.OK).body(bodyWriter.toString())
        }
    }

    fun uploadMappingCsv(user: Intake24User, request: Request): Response {
        val tableId = request.path("tableId")

        if (tableId == null)
            return Response(Status.BAD_REQUEST)
        else {
            val form = MultipartFormBody.from(request)
            val file = form.file("file")

            return if (file != null) {
                try {
                    val parseResult = FoodCompositionCsvParser.parseMappingCsv(file.content)
                    fctService.updateColumnMapping(tableId, parseResult)

                    Response(Status.OK)
                } catch (e: CsvParseException) {
                    errorUtils.errorResponse(Status.BAD_REQUEST, e)
                }
            } else {
                errorUtils.errorResponse(Status.BAD_REQUEST, "Missing file part")
            }
        }
    }
}