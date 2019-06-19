package uk.ac.ncl.openlab.intake24.tools

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.opencsv.CSVWriter
import com.typesafe.config.Config
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import uk.ac.ncl.intake24.secureurl.SecureURLService
import uk.ac.ncl.openlab.intake24.FoodsCache
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ncl.ac.uk.intake24.foodsql.Tables
import uk.ncl.ac.uk.intake24.systemsql.Tables.*
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.max


data class FoodCompositionTable(val id: String, val description: String)

@Singleton
class FoodCompositionTableService @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient) {

    fun getFoodCompositionTables(): List<FoodCompositionTable> {
        return foodDatabase.runTransaction {
            it.selectFrom(Tables.NUTRIENT_TABLES).fetchArray().map {
                FoodCompositionTable(it.id, it.description)
            }
        }
    }

}