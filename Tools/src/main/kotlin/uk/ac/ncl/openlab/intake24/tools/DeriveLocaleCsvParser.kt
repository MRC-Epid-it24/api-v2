package uk.ac.ncl.openlab.intake24.tools

import com.opencsv.CSVReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.RuntimeException
import java.nio.charset.StandardCharsets

data class FoodCompositionTableReference(val tableId: String, val recordId: String)

data class FoodDescription(val englishDescription: String, val localDescription: String)

sealed class FoodAction {
    data class Include(val foodCode: String, val localDescription: String?, val copies: List<FoodDescription>, val localFctCode: FoodCompositionTableReference?) : FoodAction()
    data class New(val descriptions: List<FoodDescription>, val fctCode: FoodCompositionTableReference, val recipesOnly: Boolean) : FoodAction()
}

class DeriveLocaleParseException(message: String) : RuntimeException(message)


object DeriveLocaleCsvParser {

    private fun parseRow(rowIndex: Int, row: Array<String>, newFctId: String): FoodAction? {

        val foodCode = row[0].trim()
        val newFctCode = row[4].trim()
        val localDescription = row[5].trim()
        val action = row[6].trim()
        val copyDescription1 = row[7].trim()
        val copyDescription2 = row[8].trim()

        val copyDescriptions = mutableListOf(FoodDescription(localDescription, localDescription))

        if (copyDescription1.isNotBlank())
            copyDescriptions.add(FoodDescription(copyDescription1, copyDescription1))

        if (copyDescription2.isNotBlank())
            copyDescriptions.add(FoodDescription(copyDescription2, copyDescription2))


        return when (action) {
            "Retain", "Retain+subfood" ->
                FoodAction.Include(foodCode, localDescription, copyDescriptions, null)

            "Ingredient" -> FoodAction.Include(foodCode, localDescription, emptyList(), null)

            "New", "New-NDB", "New+subfood" ->
                if (newFctCode.isBlank())
                    throw DeriveLocaleParseException("Food composition table code required for new food in row $rowIndex but was not provided")
                else {
                    copyDescriptions.add(0, FoodDescription(localDescription, localDescription))
                    FoodAction.New(copyDescriptions, FoodCompositionTableReference(newFctId, newFctCode), false)
                }

            "New-Ingredient" ->
                if (newFctCode.isBlank())
                    throw DeriveLocaleParseException("Food composition table code required for new food in row $rowIndex but was not provided")
                else
                    FoodAction.New(listOf(FoodDescription(localDescription, localDescription)), FoodCompositionTableReference(newFctId, newFctCode), true)

            "Replace", "Replace+subfood" ->
                FoodAction.Include(foodCode, localDescription, copyDescriptions, FoodCompositionTableReference(newFctId, newFctCode))

            else -> null
        }
    }

    fun parseTable(input: InputStream, newFctId: String): List<FoodAction> {
        val csvRows = CSVReader(InputStreamReader(input, StandardCharsets.UTF_8)).iterator().asSequence().drop(1)

        val actions = mutableListOf<FoodAction>()

        csvRows.forEachIndexed { index, row ->
            val rowAction = parseRow(index + 1, row, newFctId)

            if (rowAction != null)
                actions.add(rowAction)
        }

        return actions
    }
}
