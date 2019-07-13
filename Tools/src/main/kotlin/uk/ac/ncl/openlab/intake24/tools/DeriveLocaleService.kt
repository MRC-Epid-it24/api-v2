package uk.ac.ncl.openlab.intake24.tools

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.services.*
import uk.ac.ncl.openlab.intake24.services.LocalesService.Companion.getLocale
import java.lang.RuntimeException
import java.time.Year
import java.util.regex.Pattern

@Singleton
class DeriveLocaleService @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient,
                                                private val localesService: LocalesService,
                                                private val foodsService: FoodsServiceV2) {

    private val logger = LoggerFactory.getLogger(DeriveLocaleService::class.java)

    private fun makeCode(englishDescription: String): String {
        val yearDigits = (Year.now().value % 100).toString()

        return (yearDigits + englishDescription.filter { it.isLetterOrDigit() || it.isWhitespace() }
                .split(Pattern.compile("\\s+"))
                .joinToString("") { it.take(1).toUpperCase() })
                .take(8)
                .padEnd(4, 'X')
    }

    private fun deduplicateCode(code: String): String {
        val last = code.last()

        if (last.isDigit()) {
            val v = last.toString().toInt()
            if (v < 9) {
                return code.dropLast(1) + (v + 1).toString()
            } else
                throw IllegalArgumentException("Ran out of code variants :(")
        } else if (code.length < 8) {
            return code + "0"
        } else {
            return code.dropLast(1) + "0"
        }
    }

    private fun deduplicateCodes(codes: Set<String>): Map<String, String> {
        return codes.fold(emptyMap()) { map, badCode ->
            map + Pair(badCode, deduplicateCode(badCode))
        }
    }

    private fun ensureUniqueInDatabase(foods: List<NewFoodV2>, attemptsLeft: Int = 10): List<NewFoodV2> {

        if (attemptsLeft == 0)
            throw IllegalArgumentException("Failed to get rid of duplicates in 10 attempts")

        val codes = foods.map { it.code }.toSet()
        val duplicateCodes = foodsService.getDuplicateCodes(codes)

        if (duplicateCodes.isNotEmpty()) {
            logger.debug("Duplicate codes exist, attempting to fix ($attemptsLeft attempts left)")

            val replacements = deduplicateCodes(duplicateCodes)

            val replaced = foods.map {
                val rep = replacements[it.code]
                if (rep != null)
                    it.copy(code = rep)
                else
                    it
            }

            return ensureUniqueInDatabase(replaced, attemptsLeft - 1)

        } else
            return foods
    }

    private fun makeUniqueCodeAndRemember(englishDescription: String, existing: MutableSet<String>): String {

        fun findUnique(attemptsLeft: Int = 10, candidate: String = makeCode(englishDescription)): String {
            return if (attemptsLeft == 0)
                throw RuntimeException("Failed to produce unique code in 10 attempts (last attempted code: $candidate)")
            else {
                if (existing.contains(candidate)) {
                    logger.debug("Tried $candidate, already taken")
                    findUnique(attemptsLeft - 1, deduplicateCode(candidate))
                }
                else {
                    logger.debug("$candidate is unique")
                    candidate
                }
            }
        }


        val code = findUnique()
        existing.add(code)
        return code
    }


    fun deriveLocale(sourceLocaleId: String, destLocaleId: String, actions: List<FoodAction>) {

        val newFoods = mutableListOf<NewFoodV2>()
        val newLocalFoods = mutableListOf<NewLocalFoodV2>()
        val foodCopies = mutableListOf<CopyFoodV2>()
        val localCopies = mutableListOf<CopyLocalV2>()
        val foodsCodesToInclude = mutableListOf<String>()

        val newCodes = mutableSetOf<String>()

        val destLocale = foodDatabase.runTransaction {
            getLocale(destLocaleId, it)
        }

        if (destLocale == null)
            throw IllegalArgumentException("Locale $destLocaleId does not exist")


        actions.filterIsInstance<FoodAction.New>().forEach { newFood ->
            newFood.descriptions.map {

                val code = makeUniqueCodeAndRemember(it.englishDescription, newCodes)

                val nutrientTableCodes = if (newFood.fctCode != null) listOf(newFood.fctCode) else emptyList()

                val attributes = InheritableAttributes(null, null, null,
                        if (newFood.recipesOnly) FoodsServiceV2.USE_AS_RECIPE_INGREDIENT else FoodsServiceV2.USE_AS_REGULAR_FOOD)

                newFoods.add(NewFoodV2(code, it.englishDescription, 1, attributes))
                newLocalFoods.add(NewLocalFoodV2(code, it.localDescription, nutrientTableCodes, emptyList(),
                        emptyList(), emptyList()))
                foodsCodesToInclude.add(code)
            }
        }

        actions.filterIsInstance<FoodAction.Include>().forEach { includeFood ->

            val nutrientTableCodes = if (includeFood.localFctCode != null) listOf(includeFood.localFctCode) else emptyList()

            if (destLocale.prototypeLocale == sourceLocaleId) {
                newLocalFoods.add(NewLocalFoodV2(includeFood.foodCode, includeFood.localDescription,
                        nutrientTableCodes, emptyList(), emptyList(), emptyList()))
            } else {
                localCopies.add(CopyLocalV2(includeFood.foodCode, includeFood.foodCode, includeFood.localDescription))
            }

            includeFood.copies.forEach {
                val copyCode = makeUniqueCodeAndRemember(it.englishDescription, newCodes)

                foodCopies.add(CopyFoodV2(includeFood.foodCode, copyCode, it.englishDescription))
                localCopies.add(CopyLocalV2(includeFood.foodCode, copyCode, it.localDescription))
            }
        }

        val newFoodsWithUniqueCodes = ensureUniqueInDatabase(newFoods)

        foodDatabase.runTransaction {
            foodsService.createFoods(newFoodsWithUniqueCodes, it)
            foodsService.copyFoods(foodCopies, it)
            foodsService.createLocalFoods(newLocalFoods, destLocaleId, it)
            foodsService.copyLocalFoods(sourceLocaleId, destLocaleId, localCopies, it)
            foodsService.addFoodsToLocale(foodsCodesToInclude, destLocaleId, it)
        }
    }
}