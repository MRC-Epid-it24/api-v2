package uk.ac.ncl.openlab.intake24.tools

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import com.opencsv.CSVReader
import uk.ac.ncl.openlab.intake24.Food
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.RuntimeException
import java.nio.charset.StandardCharsets
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

data class FoodCompositionTableReference(val tableId: String, val recordId: String)

data class FoodDescription(val englishDescription: String, val localDescription: String)

sealed class FoodAction {
    data class Include(val foodCode: String, val localDescription: String, val copies: List<FoodDescription>, val localFctCode: FoodCompositionTableReference?) : FoodAction()
    data class New(val descriptions: List<FoodDescription>, val fctCode: FoodCompositionTableReference, val recipesOnly: Boolean) : FoodAction()
    object NoAction : FoodAction()
}

class DeriveLocaleParseException(message: String) : RuntimeException(message)


object DeriveLocaleCsvParser {

    const val ROW_OFFSET = 1

    private fun parseRow(rowIndex: Int, row: Array<String>, newFctId: String): Either<String, FoodAction> {

        val sourceRowIndex = rowIndex + ROW_OFFSET

        val foodCode = row[0].trim()
        val oldDescription = row[1].trim()
        val newFctCode = row[4].trim()
        val newDescription = row[5].trim()
        val action = row[6].trim()
        val copyDescription1 = row[7].trim()
        val copyDescription2 = row[8].trim()

        fun requireNewFoodCode(): Either<String, Unit> {
            return if (foodCode.isBlank())
                Left("Food code (column A) is required in row $sourceRowIndex but is missing")
            else
                Right(Unit)
        }

        fun requireDescription(): Either<String, Unit> {
            return if (newDescription.isBlank() && oldDescription.isBlank())
                Left("Either new description (column F) or old description (column B) is required for row $sourceRowIndex but is missing")
            else
                Right(Unit)
        }

        fun requireNewDescription(): Either<String, Unit> {
            return if (newDescription.isBlank())
                Left("New food description (column F) is required for row $sourceRowIndex but is missing")
            else
                Right(Unit)
        }

        fun requireNutrientTableCode(): Either<String, Unit> {
            return if (newFctCode.isBlank())
                Left("New food composition table code (column E) is required for row $sourceRowIndex but is missing")
            else
                Right(Unit)
        }

        val copyDescriptions = mutableListOf<FoodDescription>()

        if (copyDescription1.isNotBlank())
            copyDescriptions.add(FoodDescription(copyDescription1, copyDescription1))

        if (copyDescription2.isNotBlank())
            copyDescriptions.add(FoodDescription(copyDescription2, copyDescription2))


        return when (action.toLowerCase()) {
            "retain", "retain+subfood" ->
                requireNewFoodCode().flatMap { _ -> requireDescription() }.flatMap { _ ->
                    Right(FoodAction.Include(foodCode, if (newDescription.isBlank()) oldDescription else newDescription, copyDescriptions, null))
                }


            "ingredient" ->
                requireNewFoodCode().flatMap { _ -> requireDescription() }.flatMap { _ ->
                    Right(FoodAction.Include(foodCode, if (newDescription.isBlank()) oldDescription else newDescription, emptyList(), null))
                }


            "new", "new-ndb", "new+subfood" ->
                requireNewDescription().flatMap { _ -> requireDescription() }.flatMap { _ -> requireNutrientTableCode() }.flatMap { _ ->
                    copyDescriptions.add(0, FoodDescription(newDescription, newDescription))
                    Right(FoodAction.New(copyDescriptions, FoodCompositionTableReference(newFctId, newFctCode), false))
                }

            "new-ingredient", "new-ingredient+subfood" -> {
                requireNewDescription().flatMap { _ -> requireNutrientTableCode() }.flatMap { _ ->
                    copyDescriptions.add(0, FoodDescription(newDescription, newDescription))
                    Right(FoodAction.New(copyDescriptions, FoodCompositionTableReference(newFctId, newFctCode), true))
                }
            }

            "replace", "replace+subfood" -> {
                requireNewFoodCode().flatMap { _ -> requireNewDescription() }.flatMap { _ -> requireNutrientTableCode() }.flatMap { _ ->
                    Right(FoodAction.Include(foodCode, newDescription, copyDescriptions, FoodCompositionTableReference(newFctId, newFctCode)))
                }
            }

            "exclude" -> Right(FoodAction.NoAction)

            else ->
                Left("Unexpected action in row $sourceRowIndex: $action")
        }
    }

    fun parseTable(input: InputStream, newFctId: String): Pair<List<String>, List<FoodAction>> {
        val csvRows = CSVReader(InputStreamReader(input, StandardCharsets.UTF_8)).iterator().asSequence().drop(1)

        val errors = mutableListOf<String>()
        val actions = mutableListOf<FoodAction>()

        csvRows.forEachIndexed { index, row ->
            when (val rowResult = parseRow(index + 1, row, newFctId)) {
                is Either.Right -> actions.add(rowResult.b)
                is Either.Left -> errors.add(rowResult.a)
            }
        }

        return Pair(errors, actions)
    }
}
