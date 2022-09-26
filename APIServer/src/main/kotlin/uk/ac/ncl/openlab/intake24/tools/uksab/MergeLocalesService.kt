package uk.ac.ncl.openlab.intake24.tools.uksab

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.services.CopyLocalV2
import uk.ac.ncl.openlab.intake24.services.FoodsServiceV2
import uk.ac.ncl.openlab.intake24.services.LocalesService
import uk.ac.ncl.openlab.intake24.services.PortionSizeMethodsService

data class MergeLocalesException(val errors: List<String>) : RuntimeException()

@Singleton
class MergeLocalesService @Inject() constructor(
    @Named("foods") private val foodDatabase: DatabaseClient,
    private val localesService: LocalesService,
    private val foodsService: FoodsServiceV2,
    private val psmService: PortionSizeMethodsService
) {

    private val logger = LoggerFactory.getLogger(MergeLocalesService::class.java)

    fun mergeLocales(destLocaleId: String, baseLocaleId: String, mergeLocaleId: String, foods: List<MergeLocalesRow>) {

        val issues = mutableListOf<String>()

        foods.forEachIndexed { index, food ->
            if (food.sourceLocale != baseLocaleId && food.sourceLocale != mergeLocaleId) {
                issues.add("Row ${index + 1}: source locale must be either $baseLocaleId or $mergeLocaleId")
            }
        }

        if (issues.isNotEmpty())
            throw MergeLocalesException(issues)

        val (fromBaseLocale, fromMergeLocale) = foods.partition { it.sourceLocale == baseLocaleId }

        val copyFromBase =
            fromBaseLocale.map { CopyLocalV2(it.foodCode, it.foodCode, it.localDescription, emptyList()) }

        val copyFromMerge =
            fromMergeLocale.map { CopyLocalV2(it.foodCode, it.foodCode, it.localDescription, emptyList()) }

        foodDatabase.runTransaction {
            foodsService.copyCategoryLocal(baseLocaleId, destLocaleId, it)
            foodsService.copyCategoryPortionSizeMethods(mergeLocaleId, destLocaleId, it)
            foodsService.copyLocalFoods(baseLocaleId, destLocaleId, copyFromBase, it)
            foodsService.copyLocalFoods(mergeLocaleId, destLocaleId, copyFromMerge, it)
            foodsService.addFoodsToLocale(foods.map { it.foodCode }, destLocaleId)
        }
    }
}
