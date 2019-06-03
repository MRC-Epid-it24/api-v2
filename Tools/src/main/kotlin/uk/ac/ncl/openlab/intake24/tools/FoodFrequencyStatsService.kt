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
import uk.ncl.ac.uk.intake24.systemsql.Tables.*
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.math.max

@Singleton
class FoodFrequencyStatsService @Inject() constructor(@Named("system") val systemDatabase: DatabaseClient,
                                                      private val foodsCache: FoodsCache,
                                                      private val config: Config,
                                                      private val secureURLService: SecureURLService,
                                                      private val taskStatusManager: TaskStatusManager) {

    private val foodBatchSize: Int = config.getInt("services.foodFrequency.batchSize")

    private data class FrequenciesBatch(val frequencies: Map<String, Int>, val lastId: Int)

    private fun getNextBatch(locale: String, limitToSurveys: List<String>, startAfter: Int): FrequenciesBatch {
        return systemDatabase.runTransaction { context ->
            val condition1 = SURVEYS.LOCALE.eq(locale).and(SURVEY_SUBMISSION_FOODS.ID.gt(startAfter))

            val condition2 = if (limitToSurveys.isEmpty())
                condition1
            else
                condition1.and(SURVEYS.ID.`in`(limitToSurveys))

            val rows = context.select(SURVEY_SUBMISSION_FOODS.ID, SURVEY_SUBMISSION_FOODS.CODE)
                    .from(SURVEY_SUBMISSION_FOODS)
                    .join(SURVEY_SUBMISSION_MEALS)
                    .on(SURVEY_SUBMISSION_FOODS.MEAL_ID.eq(SURVEY_SUBMISSION_MEALS.ID))
                    .join(SURVEY_SUBMISSIONS)
                    .on(SURVEY_SUBMISSION_MEALS.SURVEY_SUBMISSION_ID.eq(SURVEY_SUBMISSIONS.ID))
                    .join(SURVEYS)
                    .on(SURVEY_SUBMISSIONS.SURVEY_ID.eq(SURVEYS.ID))
                    .where(condition2)
                    .orderBy(SURVEY_SUBMISSION_FOODS.ID)
                    .limit(foodBatchSize)
                    .fetch {
                        Pair(it.value1(), it.value2())
                    }

            rows.fold(FrequenciesBatch(emptyMap<String, Int>(), 0)) { acc, row ->
                FrequenciesBatch(acc.frequencies + (row.second to (acc.frequencies.getOrDefault(row.second, 0) + 1)), max(acc.lastId, row.first))
            }
        }
    }

    private fun add(acc: Map<String, Int>, frequencies: Map<String, Int>): Map<String, Int> {
        return frequencies.entries.fold(acc) { acc, entry ->
            acc + (entry.key to (acc.getOrDefault(entry.key, 0) + entry.value))
        }
    }

    private suspend fun getFrequencies(locale: String, limitToSurveys: List<String>, startAfter: Int = 0,
                                       acc: Map<String, Int> = emptyMap()): Map<String, Int> {

        val nextBatch = getNextBatch(locale, limitToSurveys, startAfter)

        return if (nextBatch.frequencies.isEmpty())
            acc
        else
            getFrequencies(locale, limitToSurveys, nextBatch.lastId, add(acc, nextBatch.frequencies))
    }

    private fun writeFile(locale: String, frequencies: Map<String, Int>): Path {

        val result = Files.createTempFile("intake24", ".csv")
        val writer = CSVWriter(FileWriter(result.toFile()))

        writer.writeNext(arrayOf("Food code", "English description", "Local description", "Times reported"))

        foodsCache.getIndexableFoods(locale).sortedBy { it.code }.forEach { food ->
            writer.writeNext(arrayOf(food.code, food.englishDescription, food.description, (frequencies[food.code]
                    ?: 0).toString()))
        }

        writer.close()

        return result
    }


    fun exportFoodFrequency(locale: String, limitSurveyIds: List<String>): String {
        val id = taskStatusManager.registerNewTask()

        GlobalScope.async {

            val file = writeFile(locale, getFrequencies(locale, limitSurveyIds))

            val date = LocalDate.now()

            val url = secureURLService.createURL("intake24-food-frequencies-$locale-${date.dayOfMonth}-${date.monthValue}-${date.year}.csv", file)

            taskStatusManager.updateTask(id, CompletionStatus.Finished(url.toString()))
        }

        return id
    }
}