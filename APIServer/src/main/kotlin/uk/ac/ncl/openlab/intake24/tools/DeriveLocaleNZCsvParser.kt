package uk.ac.ncl.openlab.intake24.tools

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import com.opencsv.CSVReader
import uk.ac.ncl.openlab.intake24.tools.csvutils.SafeRowReader
import uk.ac.ncl.openlab.intake24.tools.csvutils.excelColumnToOffset
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

enum class NZRowAction {
    New,
    Exclude,
    Retain,
    Revise
}

object DeriveLocaleNZCsvParser {

    private const val ROW_OFFSET = 1

    private val FOOD_CODE = Pair(excelColumnToOffset("A"), "Intake24 code")
    private val LOCAL_DESC = Pair(excelColumnToOffset("C"), "Local description")
    private val FOOD_COMPOSITION_TABLE = Pair(excelColumnToOffset("D"), "FCT table")
    private val FOOD_COMPOSITION_CODE = Pair(excelColumnToOffset("E"), "FCT code")
    /*private val READY_MEAL_OPTION = Pair(excelColumnToOffset("F"), "Ready meal option")
    private val SAME_AS_BEFORE_OPTION = Pair(excelColumnToOffset("G"), "Same as before option")
    private val REASONABLE_AMOUNT = Pair(excelColumnToOffset("H"), "Reasonable amount")
    private val USE_IN_RECIPES = Pair(excelColumnToOffset("I"), "Use in recipes")
    private val ASSOC_FOOD_OR_CAT = Pair(excelColumnToOffset("J"), "Associated food/category")
    private val BRAND_NAMES = Pair(excelColumnToOffset("K"), "Brand names")*/

    private val REVISED_LOCAL_DESC = Pair(excelColumnToOffset("Y"), "Revised local description")
    private val ACTION = Pair(excelColumnToOffset("Z"), "Action")
    private val SOURCE_FOOD_CODE = Pair(excelColumnToOffset("AA"), "Reference food")

    private val CATEGORIES_START_INDEX = excelColumnToOffset("M")
    private val CATEGORIES_COLUMN_COUNT = 12

    private val TREAT_AS_BLANK = setOf("0", "#N/A")
    // private val BRAND_NAME_SEPARATOR = ","

    private fun collectCategories(row: SafeRowReader): List<String> {
        return List(CATEGORIES_COLUMN_COUNT) { CATEGORIES_START_INDEX + it }
            .mapNotNull { row.getColumn(it) }
            .filterNot { it.isBlank() }
    }

    private fun parseAction(action: String): Either<String, NZRowAction> {
        return when (action) {
            "new" -> Right(NZRowAction.New)
            "Exclude" -> Right(NZRowAction.Exclude)
            "Retain" -> Right(NZRowAction.Retain)
            "Revise" -> Right(NZRowAction.Revise)
            else -> Left("Unexpected action string: $action")
        }
    }

    private fun parseRow(rowIndex: Int, row: Array<String>): Either<String, FoodAction> {

        val sourceRowIndex = rowIndex + ROW_OFFSET

        val cleanedRow = row.map {
            if (TREAT_AS_BLANK.contains(it.trim()))
                ""
            else
                it
        }.toTypedArray()

        val row = SafeRowReader(cleanedRow, rowIndex + ROW_OFFSET)

        return row.getColumn(ACTION)
            .flatMap { parseAction(it) }
            .flatMap { action ->

                when (action) {
                    NZRowAction.New ->
                        row.getColumn(LOCAL_DESC).flatMap { localDescription ->
                            row.getColumn(SOURCE_FOOD_CODE).flatMap { sourceFoodCode ->
                                row.getOptionalColumn(FOOD_COMPOSITION_TABLE).flatMap { fctId ->
                                    row.getOptionalColumn(FOOD_COMPOSITION_CODE).map { fctCode ->
                                        val fctReference =
                                            if (fctId != null && fctCode !== null) FoodCompositionTableReference(fctId, fctCode) else null
                                        FoodAction.Clone(sourceFoodCode, FoodDescription(localDescription, localDescription), fctReference)
                                    }
                                }
                            }
                        }

                    NZRowAction.Retain ->
                        row.getColumn(FOOD_CODE).flatMap { foodCode ->
                            row.getColumn(LOCAL_DESC).map { localDescription ->
                                FoodAction.Include(foodCode, localDescription, emptyList(), null)
                            }
                        }

                    NZRowAction.Revise ->
                        row.getColumn(FOOD_CODE).flatMap { foodCode ->
                            row.getColumn(REVISED_LOCAL_DESC).flatMap { localDescription ->
                                row.getColumn(FOOD_COMPOSITION_TABLE).flatMap { fctId ->
                                    row.getColumn(FOOD_COMPOSITION_CODE).map { fctCode ->
                                        FoodAction.Include(foodCode, localDescription, emptyList(), FoodCompositionTableReference(fctId, fctCode))
                                    }
                                }
                            }
                        }

                    NZRowAction.Exclude -> Right(FoodAction.NoAction)
                }
            }
    }

    fun parseTable(input: InputStream): Pair<List<String>, List<FoodAction>> {
        val csvRows = CSVReader(InputStreamReader(input, StandardCharsets.UTF_8)).iterator().asSequence().drop(1)

        val errors = mutableListOf<String>()
        val actions = mutableListOf<FoodAction>()

        csvRows.forEachIndexed { index, row ->
            when (val rowResult = parseRow(index + 1, row)) {
                is Either.Right -> actions.add(rowResult.b)
                is Either.Left -> errors.add(rowResult.a)
            }
        }

        return Pair(errors, actions)
    }
}
