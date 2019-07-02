package uk.ac.ncl.openlab.intake24.tools

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient

@Singleton
class NdnsRationalisationService @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient) {

    private val logger = LoggerFactory.getLogger(NdnsRationalisationService::class.java)

    fun apply() {

    }
}