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
    private val ACTION = Pair(2, "Action")
    private val INDIAN_NAME_INDICES = listOf(3, 4)
    private val SRI_LANKAN_NAME_INDICES = listOf(5, 6)
    private val PAKISTANI_NAME_INDICES = listOf(7, 8)
    private val BANGLADESHI_NAME_INDICES = listOf(9)
    private val FOOD_COMPOSITION_TABLE = Pair(10, "Food composition table ID")
    private val FOOD_COMPOSITION_CODE_EXISTING = Pair(11, "Existing food composition code")
    private val FOOD_COMPOSITION_CODE_NEW = Pair(12, "New food composition code")

    private val CATEGORIES_START_INDEX = 13
    private val CATEGORIES_COLUMN_COUNT = 10

    private val TREAT_AS_BLANK = setOf("0", "#N/A")

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
                .filterNot { it.isBlank() }
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
                            row.getColumn(FOOD_COMPOSITION_TABLE).flatMap { fctId ->
                                row.getOneOf(FOOD_COMPOSITION_CODE_EXISTING, FOOD_COMPOSITION_CODE_NEW).flatMap { fctCode ->
                                    val fctReference = FoodCompositionTableReference(fctId, fctCode)


                                    val firstLocalName = localNames.firstOrNull()?.localDescription ?: englishName
                                    val alternativeLocalNames = localNames.drop(1)

                                    Right(FoodAction.Include(foodCode, firstLocalName, alternativeLocalNames, fctReference))
                                }
                            }
                        }


                    "new" -> {
                        row.getColumn(FOOD_COMPOSITION_TABLE).flatMap { fctId ->
                            row.getOneOf(FOOD_COMPOSITION_CODE_EXISTING, FOOD_COMPOSITION_CODE_NEW).flatMap { fctCode ->
                                val fctReference = FoodCompositionTableReference(fctId, fctCode)

                                // Use English name in case there are no local names, but don't include plain English name
                                // otherwise
                                val fallbackDescription = listOf(FoodDescription(englishName, englishName))

                                Right(FoodAction.New(sourceRowIndex, if (localNames.isNotEmpty()) localNames else fallbackDescription,
                                        categories, fctReference, false, emptyList()))
                            }
                        }
                    }

                    "delete" -> Right(FoodAction.NoAction)

                    else ->
                        Left("Unexpected action in row $sourceRowIndex: $action")
                }
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
