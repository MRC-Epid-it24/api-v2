package uk.ac.ncl.openlab.intake24.services

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named

import org.apache.commons.lang3.StringUtils
import org.jooq.Batch
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType
import org.slf4j.LoggerFactory
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.tools.FoodCompositionTableReference
import uk.ncl.ac.uk.intake24.foodsql.Tables.*
import java.util.*


data class InheritableAttributes(val readyMealOption: Boolean?, val sameAsBeforeOption: Boolean?, val reasonableAmount: Int?,
                                 val useInRecipes: Int?)

data class NewFoodV2(val code: String, val englishDescription: String, val groupCode: Int, val attributes: InheritableAttributes)

data class NewLocalFoodV2(val code: String, val localDescription: String?, val nutrientTableCodes: List<FoodCompositionTableReference>,
                          val portionSize: List<PortionSizeMethod>, val associatedFoods: List<AssociatedFood>, val brandNames: List<String>)

data class CopyFoodV2(val sourceCode: String, val newCode: String, val newDescription: String)

data class CopyLocalV2(val sourceCode: String, val destCode: String, val localDescription: String)


data class PortionSizeMethodParameter(val name: String, val value: String)


sealed class FoodOrCategoryCode

data class FoodCode(val code: String) : FoodOrCategoryCode()

data class CategoryCode(val code: String) : FoodOrCategoryCode()

data class AssociatedFood(val foodOrCategoryCode: FoodOrCategoryCode, val promptText: String, val linkAsMain: Boolean, val genericName: String)


data class PortionSizeMethod(val method: String, val description: String, val imageUrl: String, val useForRecipes: Boolean,
                             val conversionFactor: Double, val parameters: List<PortionSizeMethodParameter>)


data class UpdateLocalFoodV2(val code: String, val baseVersion: UUID?, val newCode: String, val localDescription: String?,
                             val nutrientTableCodes: List<FoodCompositionTableReference>, val portionSize: List<PortionSizeMethod>,
                             val associatedFoods: List<AssociatedFood>, val brandNames: List<String>)


@Singleton
class FoodsServiceV2 @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient) {

    companion object {
        const val USE_ANYWHERE = 0
        const val USE_AS_REGULAR_FOOD = 1
        const val USE_AS_RECIPE_INGREDIENT = 2

        private fun <T> assertNonEmpty(list: List<T>) {
            if (list.isEmpty())
                throw RuntimeException("This function must not be called with an empty list")
        }


        fun getPortionSizeMethods(foodCodes: List<String>, localeId: String, context: DSLContext): Map<String, List<PortionSizeMethod>> {
            if (foodCodes.isNotEmpty()) {

                val portionSizeMethodRows = context.select(FOODS_PORTION_SIZE_METHODS.ID,
                        FOODS_PORTION_SIZE_METHODS.FOOD_CODE,
                        FOODS_PORTION_SIZE_METHODS.LOCALE_ID,
                        FOODS_PORTION_SIZE_METHODS.METHOD,
                        FOODS_PORTION_SIZE_METHODS.DESCRIPTION,
                        FOODS_PORTION_SIZE_METHODS.IMAGE_URL,
                        FOODS_PORTION_SIZE_METHODS.USE_FOR_RECIPES,
                        FOODS_PORTION_SIZE_METHODS.CONVERSION_FACTOR)
                        .from(FOODS_PORTION_SIZE_METHODS)
                        .where(FOODS_PORTION_SIZE_METHODS.FOOD_CODE.`in`(foodCodes).and(FOODS_PORTION_SIZE_METHODS.LOCALE_ID.eq(localeId)))
                        .fetchArray()

                val ids = portionSizeMethodRows.map { it[FOODS_PORTION_SIZE_METHODS.ID] }

                val parameters = context.select(
                        FOODS_PORTION_SIZE_METHOD_PARAMS.PORTION_SIZE_METHOD_ID,
                        FOODS_PORTION_SIZE_METHOD_PARAMS.NAME,
                        FOODS_PORTION_SIZE_METHOD_PARAMS.VALUE)
                        .from(FOODS_PORTION_SIZE_METHODS)
                        .where(FOODS_PORTION_SIZE_METHOD_PARAMS.PORTION_SIZE_METHOD_ID.`in`(ids))
                        .fold(emptyMap<Int, List<PortionSizeMethodParameter>>()) { map, row ->
                            val id = row[FOODS_PORTION_SIZE_METHOD_PARAMS.PORTION_SIZE_METHOD_ID]
                            val list = map[id] ?: emptyList()

                            map + Pair(id, list + PortionSizeMethodParameter(row[FOODS_PORTION_SIZE_METHOD_PARAMS.NAME], row[FOODS_PORTION_SIZE_METHOD_PARAMS.VALUE]))
                        }


                return portionSizeMethodRows.fold(emptyMap()) { map, row ->

                    val foodCode = row[FOODS_PORTION_SIZE_METHODS.FOOD_CODE]
                    val methods = map[foodCode] ?: emptyList()

                    map + Pair(foodCode, methods + PortionSizeMethod(
                            row[FOODS_PORTION_SIZE_METHODS.METHOD],
                            row[FOODS_PORTION_SIZE_METHODS.DESCRIPTION],
                            row[FOODS_PORTION_SIZE_METHODS.IMAGE_URL],
                            row[FOODS_PORTION_SIZE_METHODS.USE_FOR_RECIPES],
                            row[FOODS_PORTION_SIZE_METHODS.CONVERSION_FACTOR],
                            parameters[row[FOODS_PORTION_SIZE_METHODS.ID]] ?: emptyList())

                    )
                }

            } else
                return emptyMap()
        }


        fun updatePortionSizeMethods(updates: List<Pair<String, List<PortionSizeMethod>>>, localeId: String, context: DSLContext) {
            if (updates.isNotEmpty()) {
                context.deleteFrom(FOODS_PORTION_SIZE_METHODS)
                        .where(FOODS_PORTION_SIZE_METHODS.FOOD_CODE.`in`(updates.map { it.first }).and(FOODS_PORTION_SIZE_METHODS.LOCALE_ID.eq(localeId)))
                        .execute()

                if (updates.any { it.second.isNotEmpty() }) {
                    val insertQuery1 = context.insertInto(FOODS_PORTION_SIZE_METHODS,
                            FOODS_PORTION_SIZE_METHODS.FOOD_CODE,
                            FOODS_PORTION_SIZE_METHODS.LOCALE_ID,
                            FOODS_PORTION_SIZE_METHODS.METHOD,
                            FOODS_PORTION_SIZE_METHODS.DESCRIPTION,
                            FOODS_PORTION_SIZE_METHODS.IMAGE_URL,
                            FOODS_PORTION_SIZE_METHODS.USE_FOR_RECIPES,
                            FOODS_PORTION_SIZE_METHODS.CONVERSION_FACTOR)

                    val methodCodes = updates.fold(insertQuery1) { query, update ->
                        val foodCode = update.first
                        val methods = update.second

                        methods.fold(query) { query, method ->
                            query.values(foodCode, localeId, method.method, method.description, method.imageUrl,
                                    method.useForRecipes, method.conversionFactor)
                        }
                    }.returningResult(FOODS_PORTION_SIZE_METHODS.ID).fetch().intoArray(FOODS_PORTION_SIZE_METHODS.ID)


                    val paramUpdates = methodCodes.zip(updates.flatMap { it.second.map { it.parameters } })

                    if (paramUpdates.any { it.second.isNotEmpty() }) {
                        val paramsInsertQuery1 = context.insertInto(FOODS_PORTION_SIZE_METHOD_PARAMS,
                                FOODS_PORTION_SIZE_METHOD_PARAMS.PORTION_SIZE_METHOD_ID,
                                FOODS_PORTION_SIZE_METHOD_PARAMS.NAME,
                                FOODS_PORTION_SIZE_METHOD_PARAMS.VALUE)

                        paramUpdates.fold(paramsInsertQuery1) { query, update ->

                            val methodId = update.first
                            val parameters = update.second

                            parameters.fold(query) { query, parameter ->
                                query.values(methodId, parameter.name, parameter.value)
                            }
                        }.execute()
                    }
                }
            }
        }


        fun getNutrientTableCodes(foodCodes: List<String>, localeId: String, context: DSLContext): Map<String, List<FoodCompositionTableReference>> {
            if (foodCodes.isNotEmpty()) {
                return context.select(FOODS_NUTRIENT_MAPPING.FOOD_CODE, FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_ID, FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_RECORD_ID)
                        .from(FOODS_NUTRIENT_MAPPING)
                        .where(FOODS_NUTRIENT_MAPPING.FOOD_CODE.`in`(foodCodes).and(FOODS_NUTRIENT_MAPPING.LOCALE_ID.eq(localeId)))
                        .fetchArray()
                        .fold(emptyMap()) { map, row ->
                            val code = row[FOODS_NUTRIENT_MAPPING.FOOD_CODE]
                            val list = map[code] ?: emptyList()

                            map + Pair(code, list + FoodCompositionTableReference(row[FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_ID],
                                    row[FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_RECORD_ID]))
                        }
            } else
                return emptyMap()
        }

        fun updateNutrientTableCodes(updates: List<Pair<String, List<FoodCompositionTableReference>>>, localeId: String, context: DSLContext) {
            if (updates.isNotEmpty()) {
                val deleteQuery = context.deleteFrom(FOODS_NUTRIENT_MAPPING)
                        .where(FOODS_NUTRIENT_MAPPING.FOOD_CODE.`in`(updates.map { it.first }).and(FOODS_NUTRIENT_MAPPING.LOCALE_ID.eq(localeId)))


                if (updates.any { it.second.isNotEmpty() }) {

                    val insertQuery1 = context.insertInto(FOODS_NUTRIENT_MAPPING,
                            FOODS_NUTRIENT_MAPPING.FOOD_CODE,
                            FOODS_NUTRIENT_MAPPING.LOCALE_ID,
                            FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_ID,
                            FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_RECORD_ID)


                    val insertQuery = updates.fold(insertQuery1) { query, update ->
                        val foodCode = update.first
                        val codes = update.second

                        codes.fold(query) { query, fctRef ->
                            query.values(foodCode, localeId, fctRef.tableId, fctRef.recordId)
                        }
                    }

                    context.batch(deleteQuery, insertQuery).execute()
                } else
                    deleteQuery.execute()
            }
        }

        fun getBrands(foodCodes: List<String>, localeId: String, context: DSLContext): Map<String, List<String>> {
            if (foodCodes.isNotEmpty()) {
                return context.select(BRANDS.FOOD_CODE, BRANDS.NAME)
                        .from(BRANDS)
                        .where(BRANDS.FOOD_CODE.`in`(foodCodes).and(BRANDS.LOCALE_ID.eq(localeId)))
                        .fetchArray()
                        .fold(emptyMap()) { map, row ->
                            val code = row[BRANDS.FOOD_CODE]
                            val list = map[code] ?: emptyList()
                            map + Pair(code, list + row[BRANDS.NAME])
                        }
            } else
                return emptyMap()
        }

        fun updateBrands(updates: List<Pair<String, List<String>>>, localeId: String, context: DSLContext) {
            if (updates.isNotEmpty()) {

                val deleteQuery = context.deleteFrom(BRANDS)
                        .where(BRANDS.FOOD_CODE.`in`(updates.map { it.first }).and(BRANDS.LOCALE_ID.eq(localeId)))

                if (updates.any { it.second.isNotEmpty() }) {

                    val insertQuery1 = context.insertInto(FOODS_NUTRIENT_MAPPING,
                            BRANDS.FOOD_CODE,
                            BRANDS.LOCALE_ID,
                            BRANDS.NAME)


                    val insertQuery = updates.fold(insertQuery1) { query, update ->
                        val foodCode = update.first
                        val brands = update.second

                        brands.fold(query) { query, brand ->
                            query.values(foodCode, localeId, brand)
                        }
                    }

                    context.batch(deleteQuery, insertQuery).execute()
                } else
                    deleteQuery.execute()
            }
        }

        fun getAssociatedFoods(foodCodes: List<String>, localeId: String, context: DSLContext): Map<String, List<AssociatedFood>> {
            if (foodCodes.isNotEmpty()) {
                return context.select(ASSOCIATED_FOODS.FOOD_CODE,
                        ASSOCIATED_FOODS.LOCALE_ID,
                        ASSOCIATED_FOODS.ASSOCIATED_FOOD_CODE,
                        ASSOCIATED_FOODS.ASSOCIATED_CATEGORY_CODE,
                        ASSOCIATED_FOODS.TEXT,
                        ASSOCIATED_FOODS.LINK_AS_MAIN,
                        ASSOCIATED_FOODS.GENERIC_NAME)
                        .from(ASSOCIATED_FOODS)
                        .where(ASSOCIATED_FOODS.FOOD_CODE.`in`(foodCodes).and(ASSOCIATED_FOODS.LOCALE_ID.eq(localeId)))
                        .fetchArray()
                        .fold(emptyMap()) { map, row ->
                            val code = row[ASSOCIATED_FOODS.FOOD_CODE]
                            val list = map[code] ?: emptyList()

                            val assocFoodCode = row[ASSOCIATED_FOODS.ASSOCIATED_FOOD_CODE]
                            val assocCategoryCode = row[ASSOCIATED_FOODS.ASSOCIATED_CATEGORY_CODE]

                            val foodOrCategoryCode = if (assocFoodCode != null)
                                FoodCode(assocFoodCode)
                            else
                                CategoryCode(assocCategoryCode)

                            map + Pair(code, list + AssociatedFood(foodOrCategoryCode, row[ASSOCIATED_FOODS.TEXT],
                                    row[ASSOCIATED_FOODS.LINK_AS_MAIN], row[ASSOCIATED_FOODS.GENERIC_NAME]))
                        }
            } else
                return emptyMap()
        }

        fun updateAssociatedFoods(updates: List<Pair<String, List<AssociatedFood>>>, localeId: String, context: DSLContext) {
            if (updates.isNotEmpty()) {

                val deleteQuery = context.deleteFrom(ASSOCIATED_FOODS)
                        .where(ASSOCIATED_FOODS.FOOD_CODE.`in`(updates.map { it.first }).and(ASSOCIATED_FOODS.LOCALE_ID.eq(localeId)))

                if (updates.any { it.second.isNotEmpty() }) {
                    val insertQuery1 = context.insertInto(ASSOCIATED_FOODS,
                            ASSOCIATED_FOODS.FOOD_CODE,
                            ASSOCIATED_FOODS.LOCALE_ID,
                            ASSOCIATED_FOODS.ASSOCIATED_FOOD_CODE,
                            ASSOCIATED_FOODS.ASSOCIATED_CATEGORY_CODE,
                            ASSOCIATED_FOODS.TEXT,
                            ASSOCIATED_FOODS.LINK_AS_MAIN,
                            ASSOCIATED_FOODS.GENERIC_NAME)

                    val insertQuery = updates.fold(insertQuery1) { query, update ->
                        val foodCode = update.first
                        val associatedFoods = update.second

                        associatedFoods.fold(query) { query, associatedFood ->

                            val associatedFoodCode = when (associatedFood.foodOrCategoryCode) {
                                is FoodCode -> associatedFood.foodOrCategoryCode.code
                                is CategoryCode -> null
                            }

                            val associatedCategoryCode = when (associatedFood.foodOrCategoryCode) {
                                is FoodCode -> null
                                is CategoryCode -> associatedFood.foodOrCategoryCode.code
                            }

                            query.values(foodCode, localeId, associatedFoodCode, associatedCategoryCode, associatedFood.promptText,
                                    associatedFood.linkAsMain, associatedFood.genericName)
                        }
                    }

                    context.batch(deleteQuery, insertQuery).execute()
                } else
                    deleteQuery.execute()
            }
        }
    }

    private val logger = LoggerFactory.getLogger(FoodsServiceV2::class.java)

    private fun truncateDescription(description: String?, foodCode: String): String? {
        if (description == null)
            return null;

        if (description.length > 128) {
            logger.warn("Description too long for food $foodCode, truncating:")
            logger.warn(description)
            return description.take(128)
        } else
            return description
    }

    fun getDuplicateCodes(newCodes: Set<String>): Set<String> {
        return foodDatabase.runTransaction {
            it.select(FOODS.CODE).from(FOODS).where(FOODS.CODE.`in`(newCodes))
                    .fetchArray(FOODS.CODE)
        }.toSet()
    }

    fun createFood(food: NewFoodV2) {
        createFoods(listOf(food))
    }

    fun createFoods(foods: List<NewFoodV2>) {
        foodDatabase.runTransaction {
            createFoods(foods, it)
        }
    }

    fun createFoods(foods: List<NewFoodV2>, context: DSLContext) {
        if (foods.isNotEmpty()) {


            logger.debug("Writing ${foods.size} new food records to database")

            foods.sortedBy { it.code }.forEach {

                logger.debug("${it.code} | ${it.englishDescription}")
            }

            val foodsInsertQuery = context.insertInto(FOODS, FOODS.CODE, FOODS.DESCRIPTION, FOODS.FOOD_GROUP_ID, FOODS.VERSION)

            foods.fold(foodsInsertQuery) { query, food ->
                query.values(food.code, truncateDescription(food.englishDescription, food.code), food.groupCode, UUID.randomUUID())
            }.execute()

            val attributesInsertQuery = context.insertInto(FOODS_ATTRIBUTES, FOODS_ATTRIBUTES.FOOD_CODE, FOODS_ATTRIBUTES.SAME_AS_BEFORE_OPTION,
                    FOODS_ATTRIBUTES.READY_MEAL_OPTION, FOODS_ATTRIBUTES.REASONABLE_AMOUNT, FOODS_ATTRIBUTES.USE_IN_RECIPES)

            foods.fold(attributesInsertQuery) { query, food ->
                query.values(food.code, food.attributes.sameAsBeforeOption, food.attributes.readyMealOption,
                        food.attributes.reasonableAmount, food.attributes.useInRecipes)

            }
        } else {
            logger.debug("Empty list")
        }
    }


    fun createLocalFoods(foods: List<NewLocalFoodV2>, localeId: String, context: DSLContext) {
        if (foods.isNotEmpty()) {

            val query = context.insertInto(FOODS_LOCAL, FOODS_LOCAL.FOOD_CODE, FOODS_LOCAL.LOCALE_ID,
                    FOODS_LOCAL.LOCAL_DESCRIPTION, FOODS_LOCAL.SIMPLE_LOCAL_DESCRIPTION,
                    FOODS_LOCAL.VERSION)

            foods.fold(query) { insert, food ->
                insert.values(food.code, localeId, truncateDescription(food.localDescription, food.code),
                        truncateDescription(StringUtils.stripAccents(food.localDescription), food.code), UUID.randomUUID())
            }.execute()

            updateNutrientTableCodes(foods.map { Pair(it.code, it.nutrientTableCodes) }, localeId, context)
            updateAssociatedFoods(foods.map { Pair(it.code, it.associatedFoods) }, localeId, context)
            updateBrands(foods.map { Pair(it.code, it.brandNames) }, localeId, context)
            updatePortionSizeMethods(foods.map { Pair(it.code, it.portionSize) }, localeId, context)

        } else {
            logger.debug("Empty list")
        }
    }


    fun addFoodToLocale(foodCode: String, localeId: String) {
        addFoodsToLocale(listOf(foodCode), localeId)
    }

    fun addFoodsToLocale(foodCodes: List<String>, localeId: String) {
        foodDatabase.runTransaction {
            addFoodsToLocale(foodCodes, localeId, it)
        }
    }

    fun addFoodsToLocale(foodCodes: List<String>, localeId: String, context: DSLContext) {
        if (foodCodes.isNotEmpty()) {
            val query = context.insertInto(FOODS_LOCAL_LISTS, FOODS_LOCAL_LISTS.LOCALE_ID, FOODS_LOCAL_LISTS.FOOD_CODE)

            foodCodes.fold(query) { query, foodCode ->
                query.values(localeId, foodCode)
            }.execute()
        } else {
            logger.debug("Empty foods list")
        }
    }

    fun removeFoodFromLocale(foodCode: String, localeId: String) {
        removeFoodsFromLocale(listOf(foodCode), localeId)
    }

    fun removeFoodsFromLocale(foodCodes: List<String>, localeId: String) {
        if (foodCodes.isNotEmpty()) {
            foodDatabase.runTransaction {
                it.deleteFrom(FOODS_LOCAL_LISTS)
                        .where(FOODS_LOCAL_LISTS.FOOD_CODE.`in`(foodCodes))
                        .execute()
            }
        } else {
            logger.debug("Empty foods list")
        }
    }

    fun copyFoods(foods: List<CopyFoodV2>, context: DSLContext) {
        if (foods.isNotEmpty()) {

            val sourceCodes = foods.map { it.sourceCode }

            val existingSources = context
                    .select(FOODS.CODE)
                    .from(FOODS)
                    .where(FOODS.CODE.`in`(sourceCodes))
                    .fetchArray(FOODS.CODE)

            if (existingSources.size != sourceCodes.size) {
                throw IllegalArgumentException("Invalid source food codes: ${sourceCodes.minus(existingSources).joinToString()}")
            }

            val foodsQueries = foods.map {
                context.insertInto(FOODS, FOODS.CODE, FOODS.DESCRIPTION, FOODS.FOOD_GROUP_ID, FOODS.VERSION)
                        .select(context
                                .select(inline(it.newCode), inline(it.newDescription), FOODS.FOOD_GROUP_ID, inline(UUID.randomUUID()))
                                .from(FOODS)
                                .where(FOODS.CODE.eq(it.sourceCode)))
            }

            val foodAttributeQueries = foods.map {
                context.insertInto(FOODS_ATTRIBUTES, FOODS_ATTRIBUTES.FOOD_CODE, FOODS_ATTRIBUTES.SAME_AS_BEFORE_OPTION,
                        FOODS_ATTRIBUTES.READY_MEAL_OPTION, FOODS_ATTRIBUTES.REASONABLE_AMOUNT, FOODS_ATTRIBUTES.USE_IN_RECIPES)
                        .select(context.select(inline(it.newCode), FOODS_ATTRIBUTES.SAME_AS_BEFORE_OPTION, FOODS_ATTRIBUTES.READY_MEAL_OPTION,
                                FOODS_ATTRIBUTES.REASONABLE_AMOUNT, FOODS_ATTRIBUTES.USE_IN_RECIPES)
                                .from(FOODS_ATTRIBUTES)
                                .where(FOODS_ATTRIBUTES.FOOD_CODE.eq(it.sourceCode)))
            }

            val foodCategoriesQueries = foods.map {
                context.insertInto(FOODS_CATEGORIES, FOODS_CATEGORIES.FOOD_CODE, FOODS_CATEGORIES.CATEGORY_CODE)
                        .select(context.select(inline(it.newCode), FOODS_CATEGORIES.CATEGORY_CODE)
                                .from(FOODS_CATEGORIES)
                                .where(FOODS_CATEGORIES.FOOD_CODE.eq(it.sourceCode)))
            }

            // FIXME: Duplicate codes should produce more specific exceptions (currently triggers generic HTTP 500 in API server)
            context.batch(foodsQueries + foodAttributeQueries + foodCategoriesQueries).execute()

        } else
            logger.debug("Empty list")
    }

    fun copyFoods(foods: List<CopyFoodV2>) {
        foodDatabase.runTransaction {
            copyFoods(foods, it)
        }
    }

    fun copyLocalFoods(sourceLocale: String, destLocale: String, foods: List<CopyLocalV2>, context: DSLContext) {
        if (foods.isNotEmpty()) {

            val sourceFoodCodes = foods.map { it.sourceCode }

            val sourcePortionSizeMethods = getPortionSizeMethods(sourceFoodCodes, sourceLocale, context)
            val sourceAssociatedFoods = getAssociatedFoods(sourceFoodCodes, sourceLocale, context)
            val sourceBrands = getBrands(sourceFoodCodes, sourceLocale, context)
            val sourceNutrientMapping = getNutrientTableCodes(sourceFoodCodes, sourceLocale, context)

            val newLocalFoods = foods.map {
                NewLocalFoodV2(it.destCode, it.localDescription,
                        sourceNutrientMapping[it.sourceCode] ?: error("Element not in map"),
                        sourcePortionSizeMethods[it.sourceCode] ?: error("Element not in map"),
                        sourceAssociatedFoods[it.sourceCode] ?: error("Element not in map"),
                        sourceBrands[it.sourceCode] ?: error("Element not in map")
                )
            }

            createLocalFoods(newLocalFoods, destLocale, context)
        }
    }


    fun copyLocalFoods(sourceLocale: String, destLocale: String, foods: List<CopyLocalV2>) {
        foodDatabase.runTransaction {
            copyLocalFoods(sourceLocale, destLocale, foods, it)
        }
    }

    fun updateLocalFoods(updates: List<UpdateLocalFoodV2>, localeId: String, context: DSLContext) {
        updateNutrientTableCodes(updates.map { Pair(it.code, it.nutrientTableCodes) }, localeId, context)
        updateAssociatedFoods(updates.map { Pair(it.code, it.associatedFoods) }, localeId, context)
        updateBrands(updates.map { Pair(it.code, it.brandNames) }, localeId, context)
        updatePortionSizeMethods(updates.map { Pair(it.code, it.portionSize) }, localeId, context)

        val updateQueries = updates.map { update ->
            context.update(FOODS_LOCAL)
                    .set(FOODS_LOCAL.FOOD_CODE, update.newCode)
                    .set(FOODS_LOCAL.LOCAL_DESCRIPTION, truncateDescription(update.localDescription, update.code))
                    .set(FOODS_LOCAL.SIMPLE_LOCAL_DESCRIPTION, truncateDescription(StringUtils.stripAccents(update.localDescription), update.code))
                    .set(FOODS_LOCAL.VERSION, UUID.randomUUID())
                    .where(FOODS_LOCAL.LOCALE_ID.eq(localeId).and(FOODS_LOCAL.FOOD_CODE.eq(update.code).and(FOODS_LOCAL.VERSION.eq(update.baseVersion))))
        }

        val updateResult = context.batch(updateQueries).execute().toTypedArray()

        val failed = updates.map { it.code }.zip(updateResult).filter { it.second == 0}

        if (failed.isNotEmpty()) {
            throw IllegalArgumentException("Base versions did not match for the following codes: ${failed.map{ it.first}.joinToString()}. Most likely cause is a concurrent update. Please retry.")
        }
    }

}