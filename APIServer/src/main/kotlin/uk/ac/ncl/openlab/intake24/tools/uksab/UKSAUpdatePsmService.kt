package uk.ac.ncl.openlab.intake24.tools.uksab

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.services.FoodsServiceV2
import uk.ac.ncl.openlab.intake24.services.PortionSizeMethod
import uk.ac.ncl.openlab.intake24.services.PortionSizeMethodParameter

@Singleton
class UKSAUpdatePsmService @Inject() constructor(
    @Named("foods") private val foodDatabase: DatabaseClient,
    private val foodsService: FoodsServiceV2
) {

    private val logger = LoggerFactory.getLogger(MergeLocalesService::class.java)
    private val localeId = "UKSAv2"
    private val batchSize = 200

    private val asServedReplacements = mapOf(
        "SAB_newfishcurry1" to Pair("SA_UK_newfishcurry1", "SAB_newfishcurry1_leftovers"),
        "SAB_newmeatcurry1" to Pair("SA_UK_newmeatcurry1", "SAB_newmeatcurry1_leftovers"),
        "SAB_newmeatrice1" to Pair("SA_UK_newmeatrice1", "SAB_newmeatrice1_leftovers"),
        "SAB_newstirfried1" to Pair("SA_UK_newstirfried1", "SAB_newstirfried1_leftovers"),
        "SAB_newvegrice1" to Pair("SA_UK_newvegrice1", "SAB_newvegrice1_leftovers"),
        "SAB_risotto" to Pair("SA_UK_risotto", " NDNSv1_risotto_leftovers")
    )

    private fun drop(method: PortionSizeMethod): Boolean {
        return method.method == "guide-image" && methodParamEquals(method, "guide-image-id", "Cocospns")
    }

    private fun needsReplacement(method: PortionSizeMethod): Boolean {
        return method.method == "as-served" && asServedReplacements.any { methodParamEquals(method, "serving-image-set", it.key) }
    }

    private fun replace(method: PortionSizeMethod): PortionSizeMethod {
        val setName = method.parameters.find { it.name == "serving-image-set" }!!.value

        return method.copy(parameters = method.parameters.map {
            when (it.name) {
                "serving-image-set" -> PortionSizeMethodParameter(
                    it.name,
                    asServedReplacements[setName]!!.first
                )
                "leftovers-image-set" -> PortionSizeMethodParameter(
                    it.name,
                    asServedReplacements[setName]!!.second
                )
                else -> it
            }
        })
    }

    private fun methodParamEquals(method: PortionSizeMethod, parameterName: String, parameterValue: String): Boolean {
        val param = method.parameters.find { it.name == parameterName }
        return param!!.value == parameterValue
    }

    private fun getPortionSizeMethodUpdates(methods: Map<String, List<PortionSizeMethod>>): List<Pair<String, List<PortionSizeMethod>>> {
        val updates = mutableListOf<Pair<String, List<PortionSizeMethod>>>()

        methods.entries.forEach { (foodCode, methods) ->

            val updateRequired = methods.any { drop(it) || needsReplacement(it) }

            if (updateRequired)
                updates.add(Pair(foodCode, methods.filterNot { drop(it) }.map { if (needsReplacement(it)) replace(it) else it }))
        }

        return updates
    }

    private fun processNextBatch(offset: Int, context: DSLContext) {
        logger.debug("Processing next batch, offset $offset limit $batchSize")

        val foodCodes = foodsService.getLocalFoodsList(localeId, offset, batchSize, context).map { it.code }

        if (foodCodes.isEmpty()) {
            logger.debug("Done!")
            return
        }

        val updates = getPortionSizeMethodUpdates(FoodsServiceV2.getPortionSizeMethods(foodCodes, localeId, context))

        if (updates.isNotEmpty()) {
            logger.debug("Updating ${updates.joinToString(", ")}")
            FoodsServiceV2.updatePortionSizeMethods(updates, localeId, context)
        } else {
            logger.debug("No updates for current batch")
        }

        processNextBatch(offset + foodCodes.size, context)
    }

    fun updatePortionSizes() {
        foodDatabase.runTransaction {
            processNextBatch(0, it)
        }
    }
}
