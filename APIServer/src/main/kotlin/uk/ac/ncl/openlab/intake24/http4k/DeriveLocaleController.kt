package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.tools.DeriveLocaleNDNSCsvParser
import uk.ac.ncl.openlab.intake24.tools.DeriveLocaleParseException
import uk.ac.ncl.openlab.intake24.tools.DeriveLocaleSABCsvParser
import uk.ac.ncl.openlab.intake24.tools.DeriveLocaleService

class DeriveLocaleController @Inject constructor(private val service: DeriveLocaleService,
                                                 private val errorUtils: ErrorUtils,
                                                 private val stringCodec: StringCodec) {

    private val logger = LoggerFactory.getLogger(DeriveLocaleController::class.java)

    private data class ErrorsResponse(val errors: List<String>)

    fun deriveLocale(user: Intake24User, request: Request): Response {

        val form = MultipartFormBody.from(request)
        val file = form.file("file")
        val sourceLocale = form.field("sourceLocale")
        val targetLocale = form.field("targetLocale")
        val format = form.field("format")
        val foodCompositionTable = "NDNS"

        if (file == null || sourceLocale == null || targetLocale == null || foodCompositionTable == null || format == null)
            return errorUtils.errorResponse(Status.BAD_REQUEST, "Missing required field")
        else {

            try {

                val (errors, actions) = when (format) {
                    "ndns1" -> DeriveLocaleNDNSCsvParser.parseTable(file.content, foodCompositionTable)
                    "sab1" -> DeriveLocaleSABCsvParser.parseTable(file.content, foodCompositionTable)
                    else -> throw DeriveLocaleParseException("Unexpected format value: $format")
                }


                if (errors.isNotEmpty())
                    return Response(Status.BAD_REQUEST).body(stringCodec.encode(ErrorsResponse(errors)))
                else {
                    service.deriveLocale(sourceLocale, targetLocale, actions)
                    return Response(Status.OK)
                }
            } catch (e: DeriveLocaleParseException) {
                return errorUtils.errorResponse(Status.BAD_REQUEST, e)
            }
        }

    }

}