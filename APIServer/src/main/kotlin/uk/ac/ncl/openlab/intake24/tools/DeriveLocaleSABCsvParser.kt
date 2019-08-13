package uk.ac.ncl.openlab.intake24.tools

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import com.opencsv.CSVReader
import uk.ac.ncl.openlab.intake24.tools.csvutils.SafeRowReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object DeriveLocaleSABCsvParser {

    private const val ROW_OFFSET = 1

    private val FOOD_CODE = Pair(0, "Food code")
    private val ENGLISH_NAME = Pair(1, "Food description")
    private val ACTION = Pair(3, "Action")
    private val INDIAN_NAME_INDICES = listOf(4, 5)
    private val SRI_LANKAN_NAME_INDICES = listOf(6, 7)
    private val PAKISTANI_NAME_INDICES = listOf(8, 9)
    private val BANGLADESHI_NAME_INDICES = listOf(10)
    private val BRAND_NAMES = Pair(11, "Brand names")
    private val CATEGORIES_START_INDEX = 15
    private val CATEGORIES_COLUMN_COUNT = 10
    private val CATEGORIES_NULL_VALUE = "0"

    private val LOCAL_NAME_SEPARATOR = " – "

    private fun buildLocalName(englishName: String, localNames: List<String>): String? {
        if (localNames.isEmpty())
            return null
        else {
            val localPart = localNames.joinToString(LOCAL_NAME_SEPARATOR) { it.capitalize() }
            return "$localPart ($englishName)"
        }
    }

    private fun collectCategories(row: SafeRowReader): List<String> {
        return List(CATEGORIES_COLUMN_COUNT) { CATEGORIES_START_INDEX + it }
                .mapNotNull { row.getColumn(it) }
                .filterNot { it == CATEGORIES_NULL_VALUE }
    }

    private fun parseRow(rowIndex: Int, row: Array<String>, newFctId: String): Either<String, FoodAction> {

        val sourceRowIndex = rowIndex + ROW_OFFSET
        val row = SafeRowReader(row, rowIndex + ROW_OFFSET)

        return row.getColumn(ACTION).flatMap { action ->
            row.getColumn(ENGLISH_NAME).flatMap { englishName ->


                val indianName = buildLocalName(englishName, row.collectNonBlank(INDIAN_NAME_INDICES))
                val sriLankanName = buildLocalName(englishName, row.collectNonBlank(SRI_LANKAN_NAME_INDICES))
                val pakistaniName = buildLocalName(englishName, row.collectNonBlank(PAKISTANI_NAME_INDICES))
                val bangladeshiName = buildLocalName(englishName, row.collectNonBlank(BANGLADESHI_NAME_INDICES))

                val localNames = listOfNotNull(indianName, sriLankanName, pakistaniName, bangladeshiName)
                        .map { FoodDescription(englishName, it) }

                val categories = collectCategories(row)

                when (action.toLowerCase()) {
                    "keep" ->
                        row.getColumn(FOOD_CODE).flatMap { foodCode ->


                            val firstLocalName = localNames.firstOrNull()?.localDescription ?: englishName
                            val alternativeLocalNames = localNames.drop(1)

                            Right(FoodAction.Include(foodCode, firstLocalName, alternativeLocalNames, null))
                        }


                    "new" -> {

                        // Use English name in case there are no local names, but don't include plain English name
                        // otherwise
                        val fallbackDescription = listOf(FoodDescription(englishName, englishName))

                        Right(FoodAction.New(if (localNames.isNotEmpty()) localNames else fallbackDescription,
                                FoodCompositionTableReference("NDNS", "1"), false))

                    }

                    "delete" -> Right(FoodAction.NoAction)

                    else ->
                        Left("Unexpected action in row $sourceRowIndex: $action")
                }
            }
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
