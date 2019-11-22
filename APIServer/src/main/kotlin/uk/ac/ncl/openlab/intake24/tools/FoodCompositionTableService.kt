package uk.ac.ncl.openlab.intake24.tools

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.jooq.DSLContext
import org.jooq.impl.DSL.row
import org.jooq.impl.DSL.unquotedName
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.foodsql.Keys
import uk.ac.ncl.openlab.intake24.foodsql.Tables


data class FoodCompositionTableHeader(val id: String, val description: String)


data class CsvNutrientColumnMapping(val nutrientId: Int, val columnOffset: Int)

data class CsvFieldColumnMapping(val fieldName: String, val columnOffset: Int)

data class FoodCompositionCsvMapping(val rowOffset: Int, val idColumnOffset: Int, val descriptionColumnOffset: Int,
                                     val localDescriptionColumnOffset: Int?, val nutrientColumns: List<CsvNutrientColumnMapping>,
                                     val fieldColumns: List<CsvFieldColumnMapping>)

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

            val nutrientColumns = it.select(
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.NUTRIENT_TYPE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.COLUMN_OFFSET)
                    .from(Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS)
                    .where(Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.NUTRIENT_TABLE_ID.eq(id))
                    .orderBy(Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.COLUMN_OFFSET.asc())
                    .fetchArray()
                    .map {
                        CsvNutrientColumnMapping(it[Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.NUTRIENT_TYPE_ID],
                                it[Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.COLUMN_OFFSET])
                    }

            val fieldColumns = it.select(
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.FIELD_NAME,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.COLUMN_OFFSET)
                    .from(Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS)
                    .where(Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.NUTRIENT_TABLE_ID.eq(id))
                    .orderBy(Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.COLUMN_OFFSET.asc())
                    .fetchArray()
                    .map {
                        CsvFieldColumnMapping(it[Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.FIELD_NAME],
                                it[Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.COLUMN_OFFSET])
                    }

            FoodCompositionTable(
                    tableRow[Tables.NUTRIENT_TABLES.ID],
                    tableRow[Tables.NUTRIENT_TABLES.DESCRIPTION],
                    FoodCompositionCsvMapping(
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.ROW_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.ID_COLUMN_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.DESCRIPTION_COLUMN_OFFSET],
                            tableRow[Tables.NUTRIENT_TABLE_CSV_MAPPING.LOCAL_DESCRIPTION_COLUMN_OFFSET],
                            nutrientColumns,
                            fieldColumns))

        };
    }

    private fun updateNutrientColumnMapping(tableId: String, mapping: List<CsvNutrientColumnMapping>, context: DSLContext) {
        context.deleteFrom(Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS)
                .where(Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.NUTRIENT_TABLE_ID.eq(tableId))
                .execute();

        val insert1 = context.insertInto(Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS,
                Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.NUTRIENT_TABLE_ID,
                Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.NUTRIENT_TYPE_ID,
                Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.COLUMN_OFFSET
        )

        if (mapping.isNotEmpty()) {
            mapping.fold(insert1, { acc, columnMapping ->
                acc.values(tableId, columnMapping.nutrientId, columnMapping.columnOffset)
            }).execute()
        }
    }

    fun updateNutrientColumnMapping(tableId: String, mapping: List<CsvNutrientColumnMapping>) {
        foodDatabase.runTransaction {
            updateNutrientColumnMapping(tableId, mapping, it)
        }
    }

    private fun updateFieldColumnMapping(tableId: String, mapping: List<CsvFieldColumnMapping>, context: DSLContext) {
        context.deleteFrom(Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS)
                .where(Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.NUTRIENT_TABLE_ID.eq(tableId))
                .execute();

        val insert1 = context.insertInto(Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS,
                Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.NUTRIENT_TABLE_ID,
                Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.FIELD_NAME,
                Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.COLUMN_OFFSET
        )

        if (mapping.isNotEmpty()) {
            mapping.fold(insert1, { acc, columnMapping ->
                acc.values(tableId, columnMapping.fieldName, columnMapping.columnOffset)
            }).execute()
        }
    }

    fun updateFieldColumnMapping(tableId: String, mapping: List<CsvFieldColumnMapping>) {
        foodDatabase.runTransaction {
            updateFieldColumnMapping(tableId, mapping, it)
        }
    }

    fun updateFoodCompositionTable(tableId: String, update: FoodCompositionTable) {
        foodDatabase.runTransaction {

            updateNutrientColumnMapping(tableId, update.mapping.nutrientColumns, it)
            updateFieldColumnMapping(tableId, update.mapping.fieldColumns, it)

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
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.ROW_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.ID_COLUMN_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.DESCRIPTION_COLUMN_OFFSET,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING.LOCAL_DESCRIPTION_COLUMN_OFFSET
            ).values(table.id,
                    table.mapping.rowOffset,
                    table.mapping.idColumnOffset,
                    table.mapping.descriptionColumnOffset,
                    table.mapping.localDescriptionColumnOffset)
                    .execute()

            val insert1 = it.insertInto(Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.NUTRIENT_TABLE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.NUTRIENT_TYPE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_NUTRIENT_COLUMNS.COLUMN_OFFSET
            )

            if (table.mapping.nutrientColumns.isNotEmpty()) {
                table.mapping.nutrientColumns.fold(insert1, { acc, columnMapping ->
                    acc.values(table.id, columnMapping.nutrientId, columnMapping.columnOffset)
                }).execute()
            }

            val insert2 = it.insertInto(Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.NUTRIENT_TABLE_ID,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.FIELD_NAME,
                    Tables.NUTRIENT_TABLE_CSV_MAPPING_FIELD_COLUMNS.COLUMN_OFFSET
            )

            if (table.mapping.fieldColumns.isNotEmpty()) {
                table.mapping.fieldColumns.fold(insert2, { acc, columnMapping ->
                    acc.values(table.id, columnMapping.fieldName, columnMapping.columnOffset)
                }).execute()
            }
        }
    }

    fun updateRecords(tableId: String, records: List<FoodCompositionTableRecord>) {
        foodDatabase.runTransaction {
            updateRecords(tableId, records, it)
        }
    }

    fun updateRecords(tableId: String, records: List<FoodCompositionTableRecord>, context: DSLContext) {
        val recordsInsertQuery = context.insertInto(Tables.NUTRIENT_TABLE_RECORDS,
                Tables.NUTRIENT_TABLE_RECORDS.ID,
                Tables.NUTRIENT_TABLE_RECORDS.NUTRIENT_TABLE_ID,
                Tables.NUTRIENT_TABLE_RECORDS.ENGLISH_DESCRIPTION,
                Tables.NUTRIENT_TABLE_RECORDS.LOCAL_DESCRIPTION)

        val excluded = Tables.NUTRIENT_TABLE_RECORDS.`as`(unquotedName("excluded"))

        val recordsInsertQuery2 = records.fold(recordsInsertQuery) { query, record ->
            query.values(record.recordId, tableId, record.englishDescription, record.localDescription)
        }

        recordsInsertQuery2.onConflictOnConstraint(Keys.NUTRIENT_TABLE_RECORDS_PK)
                .doUpdate()
                .set(Tables.NUTRIENT_TABLE_RECORDS.ENGLISH_DESCRIPTION, excluded.ENGLISH_DESCRIPTION)
                .set(Tables.NUTRIENT_TABLE_RECORDS.LOCAL_DESCRIPTION, excluded.LOCAL_DESCRIPTION)
                .execute()

        context.deleteFrom(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS)
                .where(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_ID.eq(tableId)
                        .and(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_RECORD_ID.`in`(records.map { it.recordId })))
                .execute()


        val nutrientsInsertQuery = context.insertInto(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS,
                Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_ID,
                Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_RECORD_ID,
                Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TYPE_ID,
                Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.UNITS_PER_100G)

        records.fold(nutrientsInsertQuery) { q1, record ->
            record.nutrients.fold(q1) { q2, col ->
                q2.values(tableId, record.recordId, col.first, col.second)
            }
        }.execute()


        context.deleteFrom(Tables.NUTRIENT_TABLE_RECORDS_FIELDS)
                .where(Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_ID.eq(tableId)
                        .and(Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_RECORD_ID.`in`(records.map { it.recordId })))
                .execute()

        val fieldsInsertQuery = context.insertInto(Tables.NUTRIENT_TABLE_RECORDS_FIELDS,
                Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_ID,
                Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_RECORD_ID,
                Tables.NUTRIENT_TABLE_RECORDS_FIELDS.FIELD_NAME,
                Tables.NUTRIENT_TABLE_RECORDS_FIELDS.FIELD_VALUE)

        records.fold(fieldsInsertQuery) { q1, record ->
            record.fields.fold(q1) { q2, col ->
                q2.values(tableId, record.recordId, col.first, col.second)
            }
        }.execute()
    }

    fun checkFoodCompositionCodes(codes: List<FoodCompositionTableReference>): List<FoodCompositionTableReference> {
        val existing = foodDatabase.runTransaction { context ->
            context.select(Tables.NUTRIENT_TABLE_RECORDS.NUTRIENT_TABLE_ID, Tables.NUTRIENT_TABLE_RECORDS.ID)
                    .from(Tables.NUTRIENT_TABLE_RECORDS)
                    .where(row(Tables.NUTRIENT_TABLE_RECORDS.NUTRIENT_TABLE_ID, Tables.NUTRIENT_TABLE_RECORDS.ID).`in`(codes.map {
                        row(it.tableId, it.recordId)
                    }))
                    .fetchSet {
                        Pair(it[Tables.NUTRIENT_TABLE_RECORDS.NUTRIENT_TABLE_ID], it[Tables.NUTRIENT_TABLE_RECORDS.ID])
                    }
        }

        return codes.filterNot {
            existing.contains(Pair(it.tableId, it.recordId))
        }
    }

    private data class NutrientsRow(val ref: FoodCompositionTableReference, val nutrientType: Int, val unitsPer100g: Double)

    fun getNutrients(references: Set<FoodCompositionTableReference>): Map<FoodCompositionTableReference, List<Pair<Int, Double>>> {
        return foodDatabase.runTransaction { context ->
            getNutrients(references, context)
        }
    }

    fun getNutrients(references: Set<FoodCompositionTableReference>, context: DSLContext): Map<FoodCompositionTableReference, List<Pair<Int, Double>>> {
        return context.select(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_ID, Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_RECORD_ID,
                Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TYPE_ID, Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.UNITS_PER_100G)
                .from(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS)
                .where(row(Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_ID, Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_RECORD_ID).`in`(references.map { r ->
                    row(r.tableId, r.recordId)
                }))
                .fetch {
                    NutrientsRow(FoodCompositionTableReference(it[Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_ID], it[Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TABLE_RECORD_ID]),
                            it[Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.NUTRIENT_TYPE_ID], it[Tables.NUTRIENT_TABLE_RECORDS_NUTRIENTS.UNITS_PER_100G])
                }
                .groupBy({ it.ref }, { Pair(it.nutrientType, it.unitsPer100g) })
    }

    private data class FieldsRow(val ref: FoodCompositionTableReference, val fieldName: String, val value: String)

    fun getFields(references: Set<FoodCompositionTableReference>): Map<FoodCompositionTableReference, List<Pair<String, String>>> {
        return foodDatabase.runTransaction { context ->
            getFields(references, context)
        }
    }

    fun getFields(references: Set<FoodCompositionTableReference>, context: DSLContext): Map<FoodCompositionTableReference, List<Pair<String, String>>> {
        return context.select(Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_ID, Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_RECORD_ID,
                Tables.NUTRIENT_TABLE_RECORDS_FIELDS.FIELD_NAME, Tables.NUTRIENT_TABLE_RECORDS_FIELDS.FIELD_VALUE)
                .from(Tables.NUTRIENT_TABLE_RECORDS_FIELDS)
                .where(row(Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_ID, Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_RECORD_ID).`in`(references.map { r ->
                    row(r.tableId, r.recordId)
                }))
                .fetch {
                    FieldsRow(FoodCompositionTableReference(it[Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_ID], it[Tables.NUTRIENT_TABLE_RECORDS_FIELDS.NUTRIENT_TABLE_RECORD_ID]),
                            it[Tables.NUTRIENT_TABLE_RECORDS_FIELDS.FIELD_NAME], it[Tables.NUTRIENT_TABLE_RECORDS_FIELDS.FIELD_VALUE])
                }
                .groupBy({ it.ref }, { Pair(it.fieldName, it.value) })
    }
}
