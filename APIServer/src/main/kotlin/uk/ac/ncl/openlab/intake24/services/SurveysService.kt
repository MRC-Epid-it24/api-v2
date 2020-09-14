package uk.ac.ncl.openlab.intake24.services

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient

import uk.ac.ncl.openlab.intake24.systemsql.Tables.SURVEY_SUBMISSIONS


@Singleton
class SurveysService @Inject() constructor(@Named("system") private val systemDatabase: DatabaseClient) {

    companion object {
        fun getSubmissionsCount(surveyId: String, context: DSLContext): Int? {
            return context.selectCount().from(SURVEY_SUBMISSIONS).where(SURVEY_SUBMISSIONS.SURVEY_ID.eq(surveyId)).fetchOne()?.value1()
        }
    }

    private val logger = LoggerFactory.getLogger(LocalesService::class.java)

    fun getSubmissionsCount(surveyId: String): Int? {
        return systemDatabase.runTransaction {
            getSubmissionsCount(surveyId, it)
        }
    }
}
