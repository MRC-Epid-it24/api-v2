package uk.ac.ncl.openlab.intake24.dbutils

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException

class DatabaseClient(databaseUrl: String, username: String, password: String?, executeLogging: Boolean, private val sqlDialect: SQLDialect) {
    private val logger = LoggerFactory.getLogger(DatabaseClient::class.java)

    val dataSource: HikariDataSource

    val jooqSettings = Settings().withExecuteLogging(executeLogging)

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
                val result = transaction(DSL.using(connection, sqlDialect, jooqSettings))
                connection.commit()
                return result
            } catch (e: Exception) {
                tryRollback(connection)
                throw e
            } finally {
                try {
                    connection.close()
                } catch (e: SQLException) {
                    logger.error("Failed to close a database connection", e)
                }

            }
        } catch (e: SQLException) {
            throw e
        }
    }
}
