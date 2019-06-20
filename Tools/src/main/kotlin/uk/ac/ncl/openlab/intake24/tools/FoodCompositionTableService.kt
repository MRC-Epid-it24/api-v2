package uk.ac.ncl.openlab.intake24.tools

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ncl.ac.uk.intake24.foodsql.Tables


data class FoodCompositionTableHeader(val id: String, val description: String)


data class CSVColumnMapping(val nutrientId: Int, val columnOffset: Int)

data class CSVTableMapping(val rowOffset: Int, val idColumnOffset: Int, val descriptionColumnOffset: Int,
                           val localDescriptionColumnOffset: Int?, val nutrientColumns: List<CSVColumnMapping>)

data class FoodCompositionTable(val id: String, val description: String, val mapping: CSVTableMapping)

data class NutrientType(val id: Int, val name: String, val unit: String)

@Singleton
class FoodCompositionTableService @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient) {

    fun getFoodCompositionTables(): List<FoodCompositionTableHeader> {
        return foodDatabase.runTransaction {
            it.selectFrom(Tables.NUTRIENT_TABLES).fetchArray().map {
                FoodCompositionTableHeader(it.id, it.description)
            }
        }
    }

    fun getNutrientTypes(): List<NutrientType> {
        return foodDatabase.runTransaction {
            it.select(Tables.NUTRIENT_TYPES.ID, Tables.NUTRIENT_TYPES.DESCRIPTION, Tables.NUTRIENT_UNITS.SYMBOL)
                    .from(Tables.NUTRIENT_TYPES)
                    .join(Tables.NUTRIENT_UNITS)
                    .on(Tables.NUTRIENT_TYPES.UNIT_ID.eq(Tables.NUTRIENT_UNITS.ID))
                    .orderBy(Tables.NUTRIENT_TYPES.DESCRIPTION.asc())
                    .fetchArray()
                    .map {
                        NutrientType(it[Tables.NUTRIENT_TYPES.ID], it[Tables.NUTRIENT_TYPES.DESCRIPTION], it[Tables.NUTRIENT_UNITS.SYMBOL])
                    }
        }
    }

    fun getFoodCompositionTable(id: String): FoodCompositionTable {
        return foodDatabase.runTransaction {
            val tableRow = it.select(Tables.NUTRIENT_TABLES.ID,
                    Tables.NUTRIENT_TABLES.DESCRIPTION,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.ROW_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.ID_COLUMN_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.DESCRIPTION_COLUMN_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.LOCAL_DESCRIPTION_COLUMN_OFFSET)
                    .from(Tables.NUTRIENT_TABLES)
                    .join(Tables.NUTRIENT_TABLE_CSV_MAPPING)
                    .on(Tables.NUTRIENT_TABLE_CSV_MAPPING.NUTRIENT_TABLE_ID.eq(Tables.NUTRIENT_TABLES.ID))
                    .where(Tables.NUTRIENT_TABLES.ID.eq(id))
                    .orderBy(Tables.NUTRIENT_TABLES.DESCRIPTION)
                    .fetchOne()

            val columns = it.select(
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TYPE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.COLUMN_OFFSET)
                    .from(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS)
                    .where(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TABLE_ID.eq(id))
                    .orderBy(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.COLUMN_OFFSET.asc())
                    .fetchArray()
                    .map {
                        CSVColumnMapping(it[Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TYPE_ID],
                                it[Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.COLUMN_OFFSET])
                    }

            FoodCompositionTable(
                    tableRow[Tables.NUTRIENT_TABLES.ID],
                    tableRow[Tables.NUTRIENT_TABLES.DESCRIPTION],
                    CSVTableMapping(
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.ROW_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.ID_COLUMN_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.DESCRIPTION_COLUMN_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.LOCAL_DESCRIPTION_COLUMN_OFFSET],
                            columns))

        };
    }

}