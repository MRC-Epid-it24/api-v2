package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.tools.uksab.MergeLocalesCsvParser
import uk.ac.ncl.openlab.intake24.tools.uksab.MergeLocalesException
import uk.ac.ncl.openlab.intake24.tools.uksab.MergeLocalesService

class MergeLocalesController @Inject constructor(
    private val service: MergeLocalesService,
    private val errorUtils: ErrorUtils,
    private val stringCodec: StringCodec
) {

    private val logger = LoggerFactory.getLogger(MergeLocalesController::class.java)

    private data class ErrorsResponse(val errors: List<String>)

    fun mergeLocales(user: Intake24User, request: Request): Response {

        val form = MultipartFormBody.from(request)
        val file = form.file("file")
        val baseLocale = form.field("baseLocale")?.value
        val mergeLocale = form.field("mergeLocale")?.value
        val destLocale = form.field("destLocale")?.value


        if (file == null || baseLocale == null || mergeLocale == null || destLocale == null)
            return errorUtils.errorResponse(Status.BAD_REQUEST, "Missing required field")
        else {
            val (errors, rows) = MergeLocalesCsvParser.parseTable(file.content)

            if (errors.isNotEmpty())
                return Response(Status.BAD_REQUEST).body(stringCodec.encode(ErrorsResponse(errors)))
            else {
                try {
                    service.mergeLocales(destLocale, baseLocale, mergeLocale, rows)
                    return Response(Status.OK)
                } catch (e: MergeLocalesException) {
                    return Response(Status.BAD_REQUEST).body(stringCodec.encode(ErrorsResponse(e.errors)))
                }
            }

        }
    }
}
