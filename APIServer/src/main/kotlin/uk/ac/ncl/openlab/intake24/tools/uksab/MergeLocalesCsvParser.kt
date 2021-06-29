package uk.ac.ncl.openlab.intake24.tools.uksab

import arrow.core.Either
import arrow.core.Right
import arrow.core.flatMap
import com.opencsv.CSVReader
import uk.ac.ncl.openlab.intake24.tools.csvutils.SafeRowReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class MergeLocalesRow(val foodCode: String, val sourceLocale: String, val localDescription: String)

object MergeLocalesCsvParser {

    private const val ROW_OFFSET = 1

    private val FOOD_CODE = Pair(0, "Intake24 code")
    private val LOCAL_DESCRIPTION = Pair(1, "Local description")
    private val SOURCE_LOCALE = Pair(2, "Source locale")
    private val NEW_LOCAL_DESCRIPTION = Pair(3, "New local description")

    private val TREAT_AS_BLANK = setOf("0", "#N/A")

    private fun parseRow(rowIndex: Int, row: Array<String>): Either<String, MergeLocalesRow> {

        val cleanedRow = row.map {
            if (TREAT_AS_BLANK.contains(it.trim()))
                ""
            else
                it
        }.toTypedArray()

        val row = SafeRowReader(cleanedRow, rowIndex + ROW_OFFSET)

        return row.getColumn(FOOD_CODE).flatMap { foodCode ->
            row.getColumn(LOCAL_DESCRIPTION).flatMap { localDescription ->
                row.getColumn(SOURCE_LOCALE).flatMap { sourceLocale ->
                    row.getOptionalColumn(NEW_LOCAL_DESCRIPTION).flatMap { newLocalDescription ->
                        Right(MergeLocalesRow(foodCode, sourceLocale, newLocalDescription ?: localDescription))
                    }
                }
            }
        }
    }

    fun parseTable(input: InputStream): Pair<List<String>, List<MergeLocalesRow>> {
        val csvRows =
            CSVReader(InputStreamReader(input, StandardCharsets.UTF_8)).iterator().asSequence().drop(ROW_OFFSET)

        val errors = mutableListOf<String>()
        val rows = mutableListOf<MergeLocalesRow>()

        csvRows.forEachIndexed { index, row ->
            when (val rowResult = parseRow(index + 1, row)) {
                is Either.Right -> rows.add(rowResult.b)
                is Either.Left -> errors.add(rowResult.a)
            }
        }

        rows.groupBy { it.foodCode }.filter { it.value.size > 1 }.keys.forEach { errors.add("Duplicate row found for food code $it")}

        return Pair(errors, rows)
    }
}
