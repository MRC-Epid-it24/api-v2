package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import com.google.inject.Singleton
import org.http4k.core.ContentType
import org.http4k.core.Response
import org.http4k.core.Status
import org.jooq.exception.DataAccessException
import org.jooq.exception.NoDataFoundException
import org.slf4j.Logger
import uk.ac.ncl.intake24.serialization.StringCodec

@Singleton
class ErrorUtils @Inject() constructor(val stringCodec: StringCodec) {
    private data class ErrorBody(val errorMessage: String)

    fun errorResponse(status: Status, errorMessage: String): Response {
        return Response(status)
                .header("Content-Type", ContentType.APPLICATION_JSON.toHeaderValue())
                .body(stringCodec.encode(ErrorBody(errorMessage)))
    }

    fun errorResponse(status: Status, cause: Exception): Response {
        return errorResponse(status, cause.message ?: cause.javaClass.simpleName)
    }


}