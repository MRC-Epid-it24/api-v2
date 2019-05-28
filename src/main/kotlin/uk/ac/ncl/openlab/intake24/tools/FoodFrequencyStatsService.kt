package uk.ac.ncl.openlab.intake24.tools

import kotlinx.coroutines.*
import org.jooq.DSLContext
import org.jooq.impl.DSL.select
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ncl.ac.uk.intake24.systemsql.Tables

class FoodFrequencyStatsService(val systemDatabase: DatabaseClient, private val taskStatusManager: TaskStatusManager) {

    suspend fun getNextBatch(context: DSLContext) {

    }

    fun exportFoodFrequency(limitSurveyIds: List<String>): Int {
        val id = taskStatusManager.registerNewTask()

        GlobalScope.async {

            systemDatabase.runTransaction {
                select(Tables.SURVEY_SUBMISSIONS.ID)
            }

            taskStatusManager.updateTask(id, CompletionStatus.Finished(null))
        }

        return id
    }
}