package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.tools.CsvParseException
import uk.ac.ncl.openlab.intake24.tools.DeriveLocaleCsvParser
import uk.ac.ncl.openlab.intake24.tools.DeriveLocaleService

class DeriveLocaleController @Inject constructor(private val deriveLocaleService: DeriveLocaleService,
                                                 private val errorUtils: ErrorUtils,
                                                 private val stringCodec: StringCodec) {

    private val logger = LoggerFactory.getLogger(DeriveLocaleController::class.java)

    fun createLocale(user: Intake24User, request: Request): Response {

        val form = MultipartFormBody.from(request)
        val file = form.file("file")
        val prototypeLocaleId = form.field("prototypeLocaleId")
        val newFctId = form.field("newFctId")
        val newLocaleId = form.field("newLocaleId")

        if (file == null || prototypeLocaleId == null || newLocaleId == null || newFctId == null)
            errorUtils.errorResponse(Status.BAD_REQUEST, "Missing required field")
        else {

            try {
                val actions = DeriveLocaleCsvParser.parseTable(file.content, newFctId)

                deriveLocaleService.


                Response(Status.OK).body(stringCodec.encode(parseResult.warnings))
            } catch (e: CsvParseException) {
                errorUtils.errorResponse(Status.BAD_REQUEST, e)
            }
        }

    }

}