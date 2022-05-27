package uk.ac.ncl.openlab.intake24.services

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.opencsv.CSVWriter
import com.typesafe.config.Config
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import uk.ac.ncl.intake24.secureurl.SecureURLService
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.systemsql.Tables.*
import uk.ac.ncl.openlab.intake24.tools.Download
import uk.ac.ncl.openlab.intake24.tools.FoodCompositionTableReference
import uk.ac.ncl.openlab.intake24.tools.FoodCompositionTableService
import uk.ac.ncl.openlab.intake24.tools.TaskStatusManager
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.ScheduledThreadPoolExecutor

@Singleton
class NutrientMappingExportService @Inject() constructor(
    @Named("foods") val foodDatabase: DatabaseClient,
    @Named("system") val systemDatabase: DatabaseClient,
    private val localesService: LocalesService,
    private val fctService: FoodCompositionTableService,
    private val foodsService: FoodsServiceV2,
    private val secureURLService: SecureURLService,
    private val config: Config,
    private val taskStatusManager: TaskStatusManager,
    private val threadPool: ScheduledThreadPoolExecutor
) {

    companion object {
        const val TASK_TYPE = "export-nutrients-mapping"
    }

    private val logger = LoggerFactory.getLogger(NutrientMappingExportService::class.java)

    private val foodBatchSize: Int = config.getInt("services.nutrientMapping.recalculateBatchSize")

    private data class FoodNutrientsRow(
        val code: String, val englishDescription: String, val localDescription: String?,
        val referenceLocaleId: String?, val fctReference: FoodCompositionTableReference?,
        val fields: Map<String, String>,
        val nutrients: Map<Int, Double>
    )

    private data class LocalNutrientType(val id: Int, val description: String, val unitSymbol: String)

    private data class LocalField(val description: String, val fieldName: String)

    private fun resolveFoodCompositionTableReferences(
        foodCodes: List<String>, localeId: String, prototypeLocaleId: String?,
        context: DSLContext
    ): Map<String, Pair<String, FoodCompositionTableReference>> {
        val localeTableCodes = FoodsServiceV2.getNutrientTableCodes(foodCodes, localeId, context);
        val prototypeTableCodes = if (prototypeLocaleId != null) {
            FoodsServiceV2.getNutrientTableCodes(foodCodes, prototypeLocaleId, context)
        } else null

        return foodCodes.fold(emptyMap()) { acc, code ->
            val localCodes = localeTableCodes[code]

            if (localCodes == null || localCodes.isEmpty()) {
                if (prototypeTableCodes == null) {
                    logger.warn("Food $code with no valid FCT reference and has no prototype locale")
                    acc
                } else {
                    val prototypeCodes = prototypeTableCodes.get(code)

                    if (prototypeCodes == null || prototypeCodes.isEmpty()) {
                        logger.warn(("Food $code no FCT reference in prototype locale $prototypeLocaleId"))
                        acc
                    } else {
                        if (prototypeCodes.size > 1) {
                            logger.warn(("Food $code has more than one FCT reference in prototype locale $prototypeLocaleId, one will be chosen arbitrarily"))
                        }
                        acc.plus(Pair(code, Pair(prototypeLocaleId!!, prototypeCodes.first())))
                    }
                }
            } else {
                if (localCodes.size > 1) {
                    logger.warn(("Food $code has more than one FCT reference in locale $localeId, one will be chosen arbitrarily"))
                }
                acc.plus(Pair(code, Pair(localeId, localCodes.first())))
            }
        }
    }

    private fun getNextBatch(localeId: String, prototypeLocaleId: String?, offset: Int): List<FoodNutrientsRow> {
        return foodDatabase.runTransaction { context ->
            val nextBatch = foodsService.getLocalFoodsList(localeId, offset, foodBatchSize, context)

            if (nextBatch.isNotEmpty()) {
                val foodCodes = nextBatch.map { it.code }

                val foodCompositionTableReferences = resolveFoodCompositionTableReferences(foodCodes, localeId, prototypeLocaleId, context)

                val referenceSet = foodCompositionTableReferences.map { it.value.second }.toSet()

                val nutrientMap = fctService.getNutrients(referenceSet, context)

                val fieldsMap = fctService.getFields(referenceSet, context)

                nextBatch.map { food ->

                    val ref = foodCompositionTableReferences[food.code]

                    val nutrients = ref?.let { nutrientMap[it.second] }

                    val fields = ref?.let { fieldsMap[it.second] }

                    FoodNutrientsRow(
                        food.code, food.englishDescription, food.localDescription, ref?.first,
                        ref?.second,
                        fields?.toMap() ?: emptyMap(),
                        nutrients?.toMap() ?: emptyMap()
                    )
                }

            } else
                emptyList()
        }
    }

    private fun exportFoodNutrientMappingImpl(
        localeId: String, prototypeLocaleId: String?,
        localFields: List<LocalField>,
        localNutrientTypes: List<LocalNutrientType>,
        writer: CSVWriter, currentOffset: Int
    ) {

        val nextBatch = getNextBatch(localeId, prototypeLocaleId, currentOffset)
        logger.debug("Fetched next food batch at offset $currentOffset with limit $foodBatchSize, actual batch size ${nextBatch.size}")

        if (nextBatch.isNotEmpty()) {
            nextBatch.forEach { row ->
                writer.writeNext(
                    arrayOf(
                        row.code, row.englishDescription, row.localDescription ?: "N/A", row.referenceLocaleId
                            ?: "N/A", row.fctReference?.tableId ?: "N/A", row.fctReference?.recordId ?: "N/A"
                    )
                        .plus(localFields.map { row.fields[it.fieldName] ?: "N/A" })
                        .plus(localNutrientTypes.map { row.nutrients[it.id]?.toString() ?: "N/A" })
                )
            }

            exportFoodNutrientMappingImpl(localeId, prototypeLocaleId, localFields, localNutrientTypes, writer, currentOffset + nextBatch.size)
        } else
            logger.debug("Export complete.")
    }

    private fun getLocalNutrientTypes(localeId: String): List<LocalNutrientType> {
        return systemDatabase.runTransaction { context ->

            context.select(LOCAL_NUTRIENT_TYPES.NUTRIENT_TYPE_ID, NUTRIENT_TYPES.DESCRIPTION, NUTRIENT_UNITS.SYMBOL)
                .from(
                    LOCAL_NUTRIENT_TYPES
                        .join(NUTRIENT_TYPES).on(LOCAL_NUTRIENT_TYPES.NUTRIENT_TYPE_ID.eq(NUTRIENT_TYPES.ID))
                        .join(NUTRIENT_UNITS).on(NUTRIENT_TYPES.UNIT_ID.eq(NUTRIENT_UNITS.ID))
                )
                .where(LOCAL_NUTRIENT_TYPES.LOCALE_ID.eq(localeId))
                .orderBy(LOCAL_NUTRIENT_TYPES.NUTRIENT_TYPE_ID)
                .fetch { LocalNutrientType(it.value1(), it.value2(), it.value3()) }

        }
    }

    private fun getLocalFields(localeId: String): List<LocalField> {
        return systemDatabase.runTransaction { context ->

            context.select(LOCAL_FIELDS.ID, LOCAL_FIELDS.DESCRIPTION, LOCAL_FIELDS.FIELD_NAME)
                .from(LOCAL_FIELDS)
                .where(LOCAL_FIELDS.LOCALE_ID.eq(localeId))
                .orderBy(LOCAL_FIELDS.ID)
                .fetch { LocalField(it.value2(), it.value3()) }
        }
    }

    fun exportMapping(ownerId: Int, localeId: String, insertBOM: Boolean): Int {
        val id = taskStatusManager.createTask(ownerId, TASK_TYPE)

        threadPool.execute {

            val file = Files.createTempFile("intake24", ".csv")
            val writer = OutputStreamWriter(FileOutputStream(file.toFile()), Charsets.UTF_8)
            val csvWriter = CSVWriter(writer)

            try {
                taskStatusManager.setStarted(id)

                val prototypeLocale = localesService.getLocale(localeId)?.prototypeLocale
                val localNutrients = getLocalNutrientTypes(localeId)
                val localFields = getLocalFields(localeId)

                logger.debug("Local nutrients count: ${localNutrients.size}, local fields count: ${localFields.size}")

                if (insertBOM) {
                    writer.append('\ufeff')
                }

                val header = arrayOf("Intake24 food code", "English description", "Local description", "Source locale", "FCT", "FCT record ID")
                    .plus(localFields.map { "${it.description}" })
                    .plus(localNutrients.map { "${it.description} (${it.unitSymbol})" })

                csvWriter.writeNext(header);

                exportFoodNutrientMappingImpl(localeId, prototypeLocale, localFields, localNutrients, csvWriter, 0)

                writer.close()

                val date = LocalDate.now()

                val url =
                    secureURLService.createURL("intake24-nutrient-mapping-$localeId-${date.dayOfMonth}-${date.monthValue}-${date.year}.csv", file)

                taskStatusManager.setSuccessful(id, Download(url.toString(), OffsetDateTime.now().plusHours(2)))
            } catch (e: Exception) {
                logger.error("Food nutrient mapping export failed", e)
                taskStatusManager.setFailed(id, e)
            }
        }

        return id
    }
}
