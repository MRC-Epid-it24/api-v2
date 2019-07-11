package uk.ac.ncl.openlab.intake24.tools

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.services.*
import java.time.Year
import java.util.regex.Pattern

data class Locale(val id: String, val englishName: String, val localName: String, val respondentLanguage: String,
                  val adminLanguage: String, val flagCode: String, val prototypeLocale: String?, val textDirection: String)

@Singleton
class DeriveLocaleService @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient,
                                                private val foodsService: FoodsServiceV2) {

    private val logger = LoggerFactory.getLogger(DeriveLocaleService::class.java)

    private data class LocalFoodCopy(val sourceLocaleId: String, val targetLocaleId: String,
                                     val sourceFoodCode: String, val targetFoodCode: String)

    private data class FoodCopy(val sourceFoodCode: String, val targetFoodCode: String)


    private fun makeCode(englishDescription: String): String {
        val yearDigits = (Year.now().value % 100).toString()

        return yearDigits + englishDescription.filter { it.isLetterOrDigit() || it.isWhitespace() }
                .split(Pattern.compile("\\s+"))
                .joinToString("") { it.take(1).toUpperCase() }
                .take(8)
    }

    fun deriveLocale(sourceLocaleId: String, targetLocaleId: String, actions: List<FoodAction>) {

        val newFoods = mutableListOf<NewFoodV2>()
        val newLocalFoods = mutableListOf<NewLocalFoodV2>()
        val foodCopies = mutableListOf<FoodCopy>()
        val localCopies = mutableListOf<LocalFoodCopy>()
        val foodsCodesToInclude = mutableListOf<String>()

        actions.filterIsInstance<FoodAction.New>().forEach { newFood ->
            newFood.descriptions.map {
                val code = makeCode(it.englishDescription)

                val attributes = InheritableAttributes(null, null, null,
                        if (newFood.recipesOnly) FoodsServiceV2.USE_AS_RECIPE_INREDIENT else FoodsServiceV2.USE_AS_REGULAR_FOOD)

                newFoods.add(NewFoodV2(code, it.englishDescription, 1, attributes))
                newLocalFoods.add(NewLocalFoodV2(code, it.localDescription, emptyList()))
                foodsCodesToInclude.add(code)
            }
        }

        actions.filterIsInstance<FoodAction.Include>().forEach { includeFood ->
            newLocalFoods.add(NewLocalFoodV2(includeFood.foodCode, includeFood.localDescription,
                    if (includeFood.localFctCode != null) listOf(includeFood.localFctCode) else emptyList()))

            includeFood.copies.forEach {
                val copyCode = makeCode(it.englishDescription)

                foodCopies.add(FoodCopy(includeFood.foodCode, copyCode))
                localCopies.add(LocalFoodCopy(sourceLocaleId, targetLocaleId, includeFood.foodCode, copyCode))
            }
        }


        foodDatabase.runTransaction {
            foodsService.createFoods(newFoods, it)
            foodsService.createLocalFoods(newLocalFoods, targetLocaleId, it)
            foodsService.cloneFoods()
        }
    }
}