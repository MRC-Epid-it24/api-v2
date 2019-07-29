package uk.ac.ncl.openlab.intake24.db.foods.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.sql.ResultSet

class V62_1__Init_foods_local_lists : BaseJavaMigration() {

    val logger = LoggerFactory.getLogger(javaClass)

    fun <T> getRows(rs: ResultSet, parser: (ResultSet) -> T): List<T> {
        val result = mutableListOf<T>()

        while (rs.next()) {
            result.add(parser.invoke(rs))
        }

        return result;
    }

    private fun processLocale(localeId: String, context: Context) {

        val statement = context.connection.createStatement()

        val query = """
            select code from foods
              left join foods_local as t1 on foods.code = t1.food_code and t1.locale_id = '$localeId'
              left join foods_local as t2 on foods.code = t2.food_code and t2.locale_id in (select prototype_locale_id from locales where id = '$localeId')
              left join foods_restrictions on foods.code = foods_restrictions.food_code
                where
                  (t1.local_description is not null or t2.local_description is not null)
                  and not (coalesce(t1.do_not_use, t2.do_not_use, false))
                  and (foods_restrictions.locale_id = '$localeId' or foods_restrictions.locale_id is null)
          """.trimIndent()

        val foodCodes = getRows(statement.executeQuery(query)) {
            it.getString(1)!!
        }

        statement.close()

        logger.info("Found ${foodCodes.size} foods to be used in $localeId")

        if (foodCodes.isNotEmpty()) {
            val query = foodCodes.map {
                "('$localeId', '$it')"
            }.joinToString(", ", "insert into foods_local_lists(locale_id, food_code) values ", ";")

            val statement = context.connection.createStatement()
            statement.execute(query)
            statement.close()
        }
    }

    override fun migrate(context: Context) {

        val localesStmt = context.connection.createStatement()

        val locales = getRows(localesStmt.executeQuery("select id from locales")) {
            it.getString(1)!!
        }

        localesStmt.close()

        locales.forEach { processLocale(it, context) }
    }
}