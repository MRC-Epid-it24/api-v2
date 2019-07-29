package uk.ac.ncl.openlab.intake24.services

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.foodsql.Tables.LOCALES


data class Locale(val id: String, val englishName: String, val localName: String, val respondentLanguage: String,
                  val adminLanguage: String, val flagCode: String, val prototypeLocale: String?, val textDirection: String)

@Singleton
class LocalesService @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient) {

    companion object {
        fun getLocale(id: String, context: DSLContext): Locale? {
            return context.selectFrom(LOCALES).where(LOCALES.ID.eq(id)).fetchAny {
                Locale(it.id, it.englishName, it.localName, it.respondentLanguageId, it.adminLanguageId,
                        it.countryFlagCode, it.prototypeLocaleId, it.textDirection)

            }
        }
    }


    private val logger = LoggerFactory.getLogger(LocalesService::class.java)

    fun getLocale(id: String): Locale? {
        return foodDatabase.runTransaction {
            getLocale(id, it)
        }
    }
}