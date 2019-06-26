package uk.ac.ncl.openlab.intake24.tools

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ncl.ac.uk.intake24.foodsql.Keys
import uk.ncl.ac.uk.intake24.foodsql.Tables


data class FoodCompositionTableHeader(val id: String, val description: String)


data class CsvColumnMapping(val nutrientId: Int, val columnOffset: Int)

data class FoodCompositionCsvMapping(val rowOffset: Int, val idColumnOffset: Int, val descriptionColumnOffset: Int,
                                     val localDescriptionColumnOffset: Int?, val nutrientColumns: List<CsvColumnMapping>)

data class FoodCompositionTable(val id: String, val description: String, val mapping: FoodCompositionCsvMapping)

data class NutrientType(val id: Int, val name: String, val unit: String)

@Singleton
class FoodCompositionTableService @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient) {

    private val logger = LoggerFactory.getLogger(FoodFrequencyStatsService::class.java)

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
                    .fetchSingle()

            val columns = it.select(
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TYPE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.COLUMN_OFFSET)
                    .from(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS)
                    .where(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TABLE_ID.eq(id))
                    .orderBy(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.COLUMN_OFFSET.asc())
                    .fetchArray()
                    .map {
                        CsvColumnMapping(it[Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TYPE_ID],
                                it[Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.COLUMN_OFFSET])
                    }

            FoodCompositionTable(
                    tableRow[Tables.NUTRIENT_TABLES.ID],
                    tableRow[Tables.NUTRIENT_TABLES.DESCRIPTION],
                    FoodCompositionCsvMapping(
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.ROW_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.ID_COLUMN_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.DESCRIPTION_COLUMN_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.LOCAL_DESCRIPTION_COLUMN_OFFSET],
                            columns))

        };
    }

    fun updateFoodCompositionTable(tableId: String, update: FoodCompositionTable) {
        foodDatabase.runTransaction {
            it.deleteFrom(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS)
                    .where(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TABLE_ID.eq(tableId))
                    .execute();

            val insert1 = it.insertInto(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TABLE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TYPE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.COLUMN_OFFSET
            )

            if (update.mapping.nutrientColumns.isNotEmpty()) {
                update.mapping.nutrientColumns.fold(insert1, { acc, columnMapping ->
                    acc.values(tableId, columnMapping.nutrientId, columnMapping.columnOffset)
                }).execute()
            }

            it.update(Tables.NUTRIENT_TABLE_CSV_MAPPING)
                    .set(Tables.NUTRIENT_TABLE_CSV_MAPPING.ROW_OFFSET, update.mapping.rowOffset)
                    .set(Tables.NUTRIENT_TABLE_CSV_MAPPING.ID_COLUMN_OFFSET, update.mapping.idColumnOffset)
                    .set(Tables.NUTRIENT_TABLE_CSV_MAPPING.DESCRIPTION_COLUMN_OFFSET, update.mapping.descriptionColumnOffset)
                    .set(Tables.NUTRIENT_TABLE_CSV_MAPPING.LOCAL_DESCRIPTION_COLUMN_OFFSET, update.mapping.localDescriptionColumnOffset)
                    .where(Tables.NUTRIENT_TABLE_CSV_MAPPING.NUTRIENT_TABLE_ID.eq(tableId))
                    .execute()

            it.update(Tables.NUTRIENT_TABLES)
                    .set(Tables.NUTRIENT_TABLES.ID, update.id)
                    .set(Tables.NUTRIENT_TABLES.DESCRIPTION, update.description)
                    .where(Tables.NUTRIENT_TABLES.ID.eq(tableId))
                    .execute()
        }
    }

    fun createFoodCompositionTable(table: FoodCompositionTable) {

        logger.debug(table.toString())

        foodDatabase.runTransaction {
            it.insertInto(Tables.NUTRIENT_TABLES,
                    Tables.NUTRIENT_TABLES.ID,
                    Tables.NUTRIENT_TABLES.DESCRIPTION)
                    .values(table.id, table.description)
                    .execute()

            it.insertInto(Tables.NUTRIENT_TABLE_CSV_MAPPING,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.NUTRIENT_TABLE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.ID_COLUMN_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.ROW_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.DESCRIPTION_COLUMN_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.LOCAL_DESCRIPTION_COLUMN_OFFSET
            ).values(table.id,
                    table.mapping.rowOffset,
                    table.mapping.idColumnOffset,
                    table.mapping.descriptionColumnOffset,
                    table.mapping.localDescriptionColumnOffset)
                    .execute()

            val insert1 = it.insertInto(Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TABLE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.NUTRIENT_TYPE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_COLUMNS.COLUMN_OFFSET
            )

            if (table.mapping.nutrientColumns.isNotEmpty()) {
                table.mapping.nutrientColumns.fold(insert1, { acc, columnMapping ->
                    acc.values(table.id, columnMapping.nutrientId, columnMapping.columnOffset)
                }).execute()
            }
        }
    }

    fun updateNutrientRecords(tableId: String, records: List<FoodCompositionTableRecord>) {
        foodDatabase.runTransaction {
            records.forEach { record ->
                it.insertInto(Tables.NUTRIENT_TABLE_RECORDS,
                        Tables.NUTRIENT_TABLE_RECORDS.ID,
                        Tables.NUTRIENT_TABLE_RECORDS.NUTRIENT_TABLE_ID,
                        Tables.NUTRIENT_TABLE_RECORDS.ENGLISH_DESCRIPTION,
                        Tables.NUTRIENT_TABLE_RECORDS.LOCAL_DESCRIPTION)
                        .values(record.recordId, tableId, record.englishDescription, record.localDescription)
                        .onConflictOnConstraint(Keys.NUTRIENT_TABLE_RECORDS_PK)
                        .doUpdate()
                        .set(Tables.NUTRIENT_TABLE_RECORDS.ENGLISH_DESCRIPTION, record.englishDescription)
                        .set(Tables.NUTRIENT_TABLE_RECORDS.LOCAL_DESCRIPTION, record.localDescription)
                        .execute()

                it.deleteFrom(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS)
                        .where(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_ID.eq(tableId)
                                .and(Tables.NUTRIENT_TABLE_RECORDS.ID.`in`(records.map { it.recordId })))
                        .execute()

                val insert1 = it.insertInto(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS,
                        Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_ID,
                        Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_RECORD_ID,
                        Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TYPE_ID,
                        Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.UNITS_PER_100G)

                record.nutrients.fold(insert1) { insert, col ->
                    insert.values(tableId, record.recordId, col.first, col.second)
                }.execute()

            }
        }
    }

}