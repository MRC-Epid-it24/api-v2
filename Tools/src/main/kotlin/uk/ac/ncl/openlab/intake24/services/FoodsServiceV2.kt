package uk.ac.ncl.openlab.intake24.services

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named

import org.apache.commons.lang3.StringUtils
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

data class NewLocalFoodV2(val code: String, val localDescription: String?, val fctReferences: List<FoodCompositionTableReference>)

data class CopyFoodV2(val sourceCode: String, val newCode: String, val newDescription: String)

data class CopyLocalV2(val sourceCode: String, val destCode: String, val localDescription: String)


@Singleton
class FoodsServiceV2 @Inject() constructor(@Named("foods") private val foodDatabase: DatabaseClient) {

    companion object {
        const val USE_ANYWHERE = 0
        const val USE_AS_REGULAR_FOOD = 1
        const val USE_AS_RECIPE_INREDIENT = 2


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

            val localFoodsQueries = foods.map {
                context.insertInto(FOODS_LOCAL, FOODS_LOCAL.FOOD_CODE, FOODS_LOCAL.LOCALE_ID,
                        FOODS_LOCAL.LOCAL_DESCRIPTION, FOODS_LOCAL.SIMPLE_LOCAL_DESCRIPTION, FOODS_LOCAL.VERSION)
                        .values(it.destCode,
                                destLocale,
                                truncateDescription(it.localDescription, it.destCode),
                                truncateDescription(StringUtils.stripAccents(it.localDescription), it.destCode),
                                UUID.randomUUID())
            }

            val nutrientMappingQueries = foods.map {
                context.insertInto(FOODS_NUTRIENT_MAPPING, FOODS_NUTRIENT_MAPPING.FOOD_CODE, FOODS_NUTRIENT_MAPPING.LOCALE_ID,
                        FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_ID, FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_RECORD_ID)
                        .select(context.select(
                                inline(it.destCode),
                                inline(destLocale),
                                FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_ID,
                                FOODS_NUTRIENT_MAPPING.NUTRIENT_TABLE_RECORD_ID)
                                .from(FOODS_NUTRIENT_MAPPING)
                                .where(FOODS_NUTRIENT_MAPPING.FOOD_CODE.eq(it.sourceCode).and(FOODS_NUTRIENT_MAPPING.LOCALE_ID.eq(sourceLocale))))
            }

            context.batch(localFoodsQueries + nutrientMappingQueries).execute()

            val copyTable = name("copy").fields("src_code", "dst_code").`as`(selectFrom(
                    values(*foods.map {
                        row(it.sourceCode, it.destCode)
                    }.toTypedArray())
            ))

            // FIXME: Performance: No need to copy data via application

            val sourceMethods = context.with(copyTable).select(
                    copyTable.field("dst_code").coerce(SQLDataType.VARCHAR),
                    FOODS_PORTION_SIZE_METHODS.ID,
                    FOODS_PORTION_SIZE_METHODS.FOOD_CODE,
                    FOODS_PORTION_SIZE_METHODS.LOCALE_ID,
                    FOODS_PORTION_SIZE_METHODS.METHOD,
                    FOODS_PORTION_SIZE_METHODS.DESCRIPTION,
                    FOODS_PORTION_SIZE_METHODS.IMAGE_URL,
                    FOODS_PORTION_SIZE_METHODS.USE_FOR_RECIPES,
                    FOODS_PORTION_SIZE_METHODS.CONVERSION_FACTOR)
                    .from(FOODS_PORTION_SIZE_METHODS.join(copyTable).on(FOODS_PORTION_SIZE_METHODS.FOOD_CODE.eq(copyTable.field("src_code").coerce(SQLDataType.VARCHAR))))
                    .where(FOODS_PORTION_SIZE_METHODS.LOCALE_ID.eq(sourceLocale))
                    .fetchArray()

            val insert1 = context.insertInto(FOODS_PORTION_SIZE_METHODS,
                    FOODS_PORTION_SIZE_METHODS.FOOD_CODE,
                    FOODS_PORTION_SIZE_METHODS.LOCALE_ID,
                    FOODS_PORTION_SIZE_METHODS.METHOD,
                    FOODS_PORTION_SIZE_METHODS.DESCRIPTION,
                    FOODS_PORTION_SIZE_METHODS.IMAGE_URL,
                    FOODS_PORTION_SIZE_METHODS.USE_FOR_RECIPES,
                    FOODS_PORTION_SIZE_METHODS.CONVERSION_FACTOR)

            val newMethodIds = sourceMethods.fold(insert1) { q, row ->
                q.values(row.value1(),
                        destLocale,
                        row[FOODS_PORTION_SIZE_METHODS.METHOD],
                        row[FOODS_PORTION_SIZE_METHODS.DESCRIPTION],
                        row[FOODS_PORTION_SIZE_METHODS.IMAGE_URL],
                        row[FOODS_PORTION_SIZE_METHODS.USE_FOR_RECIPES],
                        row[FOODS_PORTION_SIZE_METHODS.CONVERSION_FACTOR])
            }.returningResult(FOODS_PORTION_SIZE_METHODS.ID).fetch().intoArray(FOODS_PORTION_SIZE_METHODS.ID)

            val methodCopies = sourceMethods.map { it[FOODS_PORTION_SIZE_METHODS.ID] }.zip(newMethodIds)

            val methodCopyTable = name("copy").fields("src_method_id", "dst_method_id").`as`(selectFrom(
                    values(*methodCopies.map {
                        row(it.first, it.second)
                    }.toTypedArray())
            ))

            context.with(methodCopyTable).insertInto(FOODS_PORTION_SIZE_METHOD_PARAMS,
                    FOODS_PORTION_SIZE_METHOD_PARAMS.PORTION_SIZE_METHOD_ID,
                    FOODS_PORTION_SIZE_METHOD_PARAMS.NAME,
                    FOODS_PORTION_SIZE_METHOD_PARAMS.VALUE)
                    .select(
                            context.select(methodCopyTable.field("dst_method_id").coerce(SQLDataType.INTEGER),
                                    FOODS_PORTION_SIZE_METHOD_PARAMS.NAME,
                                    FOODS_PORTION_SIZE_METHOD_PARAMS.VALUE)
                                    .from(methodCopyTable.join(FOODS_PORTION_SIZE_METHOD_PARAMS)
                                            .on(FOODS_PORTION_SIZE_METHOD_PARAMS.PORTION_SIZE_METHOD_ID.eq(methodCopyTable.field("src_method_id").coerce(SQLDataType.INTEGER))))
                    ).execute()

        } else
            logger.debug("Empty list")
    }


    fun copyLocalFoods(sourceLocale: String, destLocale: String, foods: List<CopyLocalV2>) {
        foodDatabase.runTransaction {
            copyLocalFoods(sourceLocale, destLocale, foods, it)
        }
    }

}