package uk.ac.ncl.openlab.intake24.dbutils

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException

class DatabaseClient(databaseUrl: String, username: String, password: String?, private val sqlDialect: SQLDialect) {
    private val logger = LoggerFactory.getLogger(DatabaseClient::class.java)

    val dataSource: HikariDataSource

    init {
        val hikariConf = HikariConfig()
        hikariConf.setJdbcUrl(databaseUrl)
        hikariConf.setUsername(username)

        if (password != null)
            hikariConf.setPassword(password)

        dataSource = HikariDataSource(hikariConf)
    }

    private fun tryRollback(connection: Connection) {
        try {
            connection.rollback()
        } catch (rbe: SQLException) {
            logger.error("Transaction rollback attempt failed after database exception", rbe)
        }
    }

    fun <T> runTransaction(transaction: (DSLContext) -> T): T {
        try {
            val connection = dataSource.getConnection()

            try {
                connection.setAutoCommit(false)
                val result = transaction(DSL.using(connection, sqlDialect))
                connection.commit()
                return result
            } catch (e: DataAccessException) {
                tryRollback(connection)
                throw DataAccessError(e)

            } catch (e: Throwable) {
                tryRollback(connection)
                throw OtherError(e)
            } finally {
                try {
                    connection.close()
                } catch (e: SQLException) {
                    logger.error("Failed to close a database connection", e)
                }

            }
        } catch (e: SQLException) {
            throw OtherError(e)
        }

    }
}
