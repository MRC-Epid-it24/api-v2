package uk.ac.ncl.openlab.intake24.services

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.tools.DeriveLocaleService
import uk.ncl.ac.uk.intake24.foodsql.Tables


data class Locale(val id: String, val englishName: String, val localName: String, val respondentLanguage: String,
                  val adminLanguage: String, val flagCode: String, val prototypeLocale: String?, val textDirection: String)

@Singleton
class LocalesService @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient,
                                           @Named("system") private val systemDatabase: DatabaseClient) {

    private val logger = LoggerFactory.getLogger(LocalesService::class.java)

}