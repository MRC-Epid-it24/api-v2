package uk.ac.ncl.openlab.intake24.dbutils

import org.jooq.exception.DataAccessException

sealed class DatabaseError private constructor(cause: Throwable) : Exception(cause)

class DataAccessError(val exception: DataAccessException) : DatabaseError(exception)

class OtherError(val exception: Throwable) : DatabaseError(exception)
