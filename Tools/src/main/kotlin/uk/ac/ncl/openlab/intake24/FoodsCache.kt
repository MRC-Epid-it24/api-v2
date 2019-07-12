package uk.ac.ncl.openlab.intake24

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ncl.ac.uk.intake24.foodsql.Tables.*
import uk.ncl.ac.uk.intake24.foodsql.tables.pojos.Foods
import uk.ncl.ac.uk.intake24.foodsql.tables.pojos.FoodsLocal
import uk.ncl.ac.uk.intake24.foodsql.tables.pojos.FoodsLocalLists

import java.util.*


data class FoodLocal(val localDescription: String?, val version: UUID)

data class Food(val code: String, val description: String, val foodGroupId: Int, val version: UUID,
                val localData: Map<String, FoodLocal>, val locales: List<String>)

data class LocalFoodHeader(val code: String, val englishDescription: String, val description: String)

@Singleton
class FoodsCache @Inject() constructor(@Named("foods") private val foodsDatabase: DatabaseClient) {

    val foods: List<Food>

    init {
        foods = foodsDatabase.runTransaction { context ->
            val foods = context.selectFrom(FOODS).fetch().into(Foods::class.java)
            val foodsLocal = context.selectFrom(FOODS_LOCAL).fetch().into(FoodsLocal::class.java).groupBy { it.foodCode }
            val foodsLocalLists = context.selectFrom(FOODS_LOCAL_LISTS).fetch().into(FoodsLocalLists::class.java).groupBy { it.foodCode }

            foods.map { foodRow ->

                val localDataRows = foodsLocal[foodRow.code]

                val localData = if (localDataRows == null)
                    emptyMap<String, FoodLocal>()
                else
                    localDataRows.associateBy({ it.localeId }, { FoodLocal(it.localDescription, it.version) })

                val locales = foodsLocalLists[foodRow.code]?.map { it.localeId } ?: emptyList<String>()

                Food(foodRow.code, foodRow.description, foodRow.foodGroupId, foodRow.version, localData, locales)
            }
        }
    }


    fun getIndexableFoods(locale: String): List<LocalFoodHeader> {
        return foods.fold(emptyList<LocalFoodHeader>()) { acc, food ->
            val localData = food.localData[locale]

            if (localData != null && localData.localDescription != null && food.locales.contains(locale))
                acc + LocalFoodHeader(food.code, food.description, localData.localDescription)
            else
                acc
        }
    }
}