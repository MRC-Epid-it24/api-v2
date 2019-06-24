package uk.ac.ncl.openlab.intake24.http4k

import org.http4k.core.Response
import org.http4k.core.Status
import org.jooq.exception.DataAccessException
import org.jooq.exception.NoDataFoundException
import org.slf4j.Logger

fun formatErrorBody(e: String): String {
    return "{\"errorMessage\":\"$e\"}"
}


fun formatErrorBody(e: Exception): String {
    val message = e.message

    if (message != null)
        return formatErrorBody(message)
    else
        return ""
}

fun translateDatabaseErrors(logger: Logger, block: () -> Response): Response {
    return try {
        block()
    } catch (e: NoDataFoundException) {
        // This is typically not an error worth logging
        Response(Status.NOT_FOUND).body(formatErrorBody("Record not found"))
    } catch (e: DataAccessException) {
        logger.error("Database error", e)
        Response(Status.INTERNAL_SERVER_ERROR).body(formatErrorBody(e))
    }
}