package uk.ac.ncl.openlab.intake24.tools

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import com.opencsv.CSVReader
import uk.ac.ncl.openlab.intake24.services.PortionSizeMethod
import uk.ac.ncl.openlab.intake24.tools.csvutils.SafeRowReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class FoodCompositionTableReference(val tableId: String, val recordId: String)

data class FoodDescription(val englishDescription: String, val localDescription: String)

sealed class FoodAction {
    data class Include(val foodCode: String, val localDescription: String, val copies: List<FoodDescription>, val localFctCode: FoodCompositionTableReference?) : FoodAction()
    data class New(val descriptions: List<FoodDescription>, val categories: List<String>, val fctCode: FoodCompositionTableReference, val recipesOnly: Boolean, val portionSizeMethods: List<PortionSizeMethod>) : FoodAction()
    object NoAction : FoodAction()
}

class DeriveLocaleParseException(message: String) : RuntimeException(message)


object DeriveLocaleNDNSCsvParser {

    private const val ROW_OFFSET = 1

    private val FOOD_CODE = Pair(0, "Food code")
    private val OLD_DESCRIPTION = Pair(1, "Old description")
    private val FCT_ID = Pair(2, "Food composition table")
    private val CURRENT_FCT_CODE = Pair(3, "Existing food composition code")
    private val NEW_FCT_CODE = Pair(4, "New food composition code")
    private val NEW_DESCRIPTION = Pair(5, "New description")
    private val ACTION = Pair(6, "Action")

    private const val COPY_DESCRIPTION_1_INDEX = 7
    private const val COPY_DESCRIPTION_2_INDEX = 8

    private val AS_SERVED_INDICES = 9..12
    private val GUIDE_INDICES = 13..14
    private val CATEGORY_INDICES = 15..24

    private val TREAT_AS_BLANK = setOf("0", "#N/A")

    private fun collectNonBlank(row: SafeRowReader, indices: Iterable<Int>): List<String> {
        val il = indices.toList()
        return row.collectNonBlank(il).filterNot { TREAT_AS_BLANK.contains(it) }
    }

    private fun parseRow(rowIndex: Int, row: Array<String>): Either<String, FoodAction> {

        val sourceRowIndex = rowIndex + ROW_OFFSET
        val row = SafeRowReader(row, rowIndex + ROW_OFFSET)

        val copyDescriptions = row.collectNonBlank(COPY_DESCRIPTION_1_INDEX, COPY_DESCRIPTION_2_INDEX).map { FoodDescription(it, it) }

        val asServedIds = collectNonBlank(row, AS_SERVED_INDICES)
        val guideIds = collectNonBlank(row, GUIDE_INDICES)
        val categories = collectNonBlank(row, CATEGORY_INDICES).distinct()

        val portionSizeMethods = emptyList<PortionSizeMethod>(); // guideIds.map(PortionSizeMethod::guideImage) + asServedIds.map(PortionSizeMethod::asServed)

        return row.getColumn(ACTION).flatMap { action ->
            when (action.toLowerCase()) {
                "retain", "retain+subfood" ->
                    row.getColumn(FOOD_CODE).flatMap { foodCode ->
                        row.getColumn(FCT_ID).flatMap { fctId ->
                            row.getColumn(CURRENT_FCT_CODE).flatMap { fctCode ->
                                row.getOneOf(OLD_DESCRIPTION, NEW_DESCRIPTION).flatMap { description ->
                                    Right(FoodAction.Include(foodCode, description, copyDescriptions, FoodCompositionTableReference(fctId, fctCode)))
                                }
                            }
                        }
                    }


                "ingredient" ->
                    row.getColumn(FOOD_CODE).flatMap { foodCode ->
                        row.getColumn(FCT_ID).flatMap { fctId ->
                            row.getColumn(CURRENT_FCT_CODE).flatMap { fctCode ->
                                row.getOneOf(OLD_DESCRIPTION, NEW_DESCRIPTION).flatMap { description ->
                                    Right(FoodAction.Include(foodCode, description, copyDescriptions, FoodCompositionTableReference(fctId, fctCode)))
                                }
                            }
                        }
                    }


                "new", "new-ndb", "new+subfood" ->
                    row.getColumn(NEW_DESCRIPTION).flatMap { newDescription ->
                        row.getColumn(FCT_ID).flatMap { fctId ->
                            row.getOneOf(CURRENT_FCT_CODE, NEW_FCT_CODE).flatMap { newFctCode ->
                                Right(FoodAction.New(copyDescriptions + FoodDescription(newDescription, newDescription),
                                        categories, FoodCompositionTableReference(fctId, newFctCode), false, portionSizeMethods))

                            }
                        }
                    }

                "new-ingredient", "new-ingredient+subfood" ->
                    row.getColumn(NEW_DESCRIPTION).flatMap { newDescription ->
                        row.getColumn(FCT_ID).flatMap { fctId ->
                            row.getOneOf(CURRENT_FCT_CODE, NEW_FCT_CODE).flatMap { newFctCode ->
                                Right(FoodAction.New(copyDescriptions + FoodDescription(newDescription, newDescription),
                                        categories, FoodCompositionTableReference(fctId, newFctCode), true, portionSizeMethods))
                            }
                        }
                    }


                "replace", "replace+subfood" -> {
                    row.getColumn(FOOD_CODE).flatMap { foodCode ->
                        row.getColumn(NEW_DESCRIPTION).flatMap { newDescription ->
                            row.getColumn(FCT_ID).flatMap { fctId ->
                                row.getColumn(NEW_FCT_CODE).flatMap { newFctCode ->
                                    Right(FoodAction.Include(foodCode, newDescription, copyDescriptions, FoodCompositionTableReference(fctId, newFctCode)))
                                }
                            }
                        }
                    }
                }

                "exclude" -> Right(FoodAction.NoAction)

                else ->
                    Left("Unexpected action in row $sourceRowIndex: $action")
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
