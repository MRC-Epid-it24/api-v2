package uk.ac.ncl.openlab.intake24.services

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.typesafe.config.Config
import org.jooq.impl.DSL.name
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.systemsql.Tables.*
import uk.ac.ncl.openlab.intake24.tools.FoodCompositionTableReference
import uk.ac.ncl.openlab.intake24.tools.FoodCompositionTableService
import uk.ac.ncl.openlab.intake24.tools.TaskStatusManager
import java.util.concurrent.ScheduledThreadPoolExecutor
import kotlin.math.max

@Singleton
class NutrientMappingService @Inject() constructor(@Named("system") val systemDatabase: DatabaseClient,
                                                   private val fctService: FoodCompositionTableService,
                                                   private val config: Config,
                                                   private val taskStatusManager: TaskStatusManager,
                                                   private val threadPool: ScheduledThreadPoolExecutor) {

    companion object {
        const val TASK_TYPE = "recalculate-nutrients"
    }

    private val logger = LoggerFactory.getLogger(NutrientMappingService.javaClass)

    private val foodBatchSize: Int = config.getInt("services.nutrientMapping.recalculateBatchSize")

    private data class RemappingFoodRow(val id: Int, val nutrientTableId: String, val nutrientTableRecordId: String, val parameterName: String, val parameterValue: String)

    private data class RemappingFood(val id: Int, val fctReference: FoodCompositionTableReference, val portionWeight: Double)

    private fun getNextBatch(surveyId: String, offset: Int): List<RemappingFood> {
        val rows = systemDatabase.runTransaction { context ->
            val foodsCte = name("foods").`as`(context.select(SURVEY_SUBMISSION_FOODS.ID,
                    SURVEY_SUBMISSION_FOODS.NUTRIENT_TABLE_ID,
                    SURVEY_SUBMISSION_FOODS.NUTRIENT_TABLE_CODE
            ).from(SURVEY_SUBMISSIONS
                    .join(SURVEY_SUBMISSION_MEALS).on(SURVEY_SUBMISSIONS.ID.eq(SURVEY_SUBMISSION_MEALS.SURVEY_SUBMISSION_ID))
                    .join(SURVEY_SUBMISSION_FOODS).on(SURVEY_SUBMISSION_MEALS.ID.eq(SURVEY_SUBMISSION_FOODS.MEAL_ID)))
                    .where(SURVEY_SUBMISSIONS.SURVEY_ID.eq(surveyId))
                    .orderBy(SURVEY_SUBMISSION_FOODS.ID)
                    .offset(offset)
                    .limit(foodBatchSize))

            context.with(foodsCte)
                    .select(foodsCte.field(SURVEY_SUBMISSION_FOODS.ID.name, SURVEY_SUBMISSION_FOODS.ID.dataType),
                            foodsCte.field(SURVEY_SUBMISSION_FOODS.NUTRIENT_TABLE_ID.name, SURVEY_SUBMISSION_FOODS.NUTRIENT_TABLE_ID.dataType),
                            foodsCte.field(SURVEY_SUBMISSION_FOODS.NUTRIENT_TABLE_CODE.name, SURVEY_SUBMISSION_FOODS.NUTRIENT_TABLE_CODE.dataType),
                            SURVEY_SUBMISSION_PORTION_SIZE_FIELDS.NAME,
                            SURVEY_SUBMISSION_PORTION_SIZE_FIELDS.VALUE)
                    .from(foodsCte.join(SURVEY_SUBMISSION_PORTION_SIZE_FIELDS).on(foodsCte.field(SURVEY_SUBMISSION_FOODS.ID.name, SURVEY_SUBMISSION_FOODS.ID.dataType)
                            .eq(SURVEY_SUBMISSION_PORTION_SIZE_FIELDS.FOOD_ID)))
                    .where(SURVEY_SUBMISSION_PORTION_SIZE_FIELDS.NAME.eq("servingWeight").or(SURVEY_SUBMISSION_PORTION_SIZE_FIELDS.NAME.eq("leftoversWeight")))
                    .fetch {
                        RemappingFoodRow(it.value1()!!, it.value2()!!, it.value3()!!, it.value4()!!, it.value5()!!)
                    }

        }

        return rows.groupBy { it.id }.map { e ->
            val servingWeight = (e.value.find { it.parameterName == "servingWeight" }?.parameterValue ?: "0").toDouble()
            val leftoversWeight = (e.value.find { it.parameterName == "leftoversWeight" }?.parameterValue
                    ?: "0").toDouble()

            RemappingFood(e.key, FoodCompositionTableReference(e.value.first().nutrientTableId, e.value.first().nutrientTableRecordId), max(0.0, servingWeight - leftoversWeight))
        }
    }

    private data class SubmissionNutrientsRow(val submissionFoodId: Int, val nutrientTypeId: Int, val amount: Double)

    private fun recalculateBatch(foods: List<RemappingFood>) {
        val batchNutrients = fctService.getNutrients(foods.map { it.fctReference }.toSet())

        val recalculated = foods.flatMap { food ->
            batchNutrients[food.fctReference]!!.map {
                SubmissionNutrientsRow(food.id, it.first, it.second / 100.0 * food.portionWeight)
            }
        }

        systemDatabase.runTransaction {

            it.deleteFrom(SURVEY_SUBMISSION_NUTRIENTS)
                    .where(SURVEY_SUBMISSION_NUTRIENTS.FOOD_ID.`in`(foods.map { it.id })).execute()

            val insert = it.insertInto(SURVEY_SUBMISSION_NUTRIENTS, SURVEY_SUBMISSION_NUTRIENTS.FOOD_ID, SURVEY_SUBMISSION_NUTRIENTS.NUTRIENT_TYPE_ID, SURVEY_SUBMISSION_NUTRIENTS.AMOUNT)

            recalculated.fold(insert) { query, row ->
                query.values(row.submissionFoodId, row.nutrientTypeId, row.amount)
            }.execute()
        }
    }

    private fun recalculateNutrientsImpl(surveyId: String, currentOffset: Int) {
        val nextBatch = getNextBatch(surveyId, currentOffset)

        if (nextBatch.isNotEmpty()) {
            recalculateBatch(nextBatch)
            recalculateNutrientsImpl(surveyId, currentOffset + nextBatch.size)
        }
    }

    fun recalculateNutrients(ownerId: Int, surveyId: String): Int {
        val id = taskStatusManager.createTask(ownerId, TASK_TYPE)

        threadPool.execute {
            try {
                taskStatusManager.setStarted(id)
                recalculateNutrientsImpl(surveyId, id)
                taskStatusManager.setSuccessful(id, null)
            } catch (e: Exception) {
                logger.error("Recalculate task failed", e)
                taskStatusManager.setFailed(id, e)
            }
        }

        return id
    }
}
