package uk.ac.ncl.openlab.intake24.tools

import com.opencsv.CSVReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets



data class FoodCompositionTableRecord(val recordId: String, val englishDescription: String, val localDescription: String?, val nutrients: List<Pair<Int, Double>>)

data class CsvParseResult(val rows: List<FoodCompositionTableRecord>, val warnings: List<String>)

class CsvParseException(message: String, cause: Throwable) : Exception(message, cause)

object FoodCompositionCsvParser {



    private fun parseRow(row: Array<String>, rowIndex: Int, mapping: FoodCompositionCsvMapping): Pair<FoodCompositionTableRecord, List<String>> {
        val nutrients = mutableListOf<Pair<Int, Double>>()
        val warnings = mutableListOf<String>()

        val recordId = row[mapping.idColumnOffset]
        val description = row[mapping.descriptionColumnOffset]
        val localDescription = if (mapping.localDescriptionColumnOffset == null) null else row[mapping.localDescriptionColumnOffset]

        mapping.nutrientColumns.forEach { col ->
            try {
                nutrients.add(Pair(col.nutrientId, row[col.columnOffset].toDouble()))
            } catch (e: Exception) {

                if (col.nutrientId == 1)
                    throw CsvParseException("Failed to parse Energy (kcal) in row $rowIndex at column offset ${col.columnOffset}. This value is required.", e)
                else
                    warnings.add("Failed to parse nutrient type ${col.nutrientId} in row $rowIndex at column offset ${col.columnOffset}: ${e.message
                            ?: e.javaClass.simpleName}")
            }
        }

        return Pair(FoodCompositionTableRecord(recordId, description, localDescription, nutrients), warnings)
    }


    fun parseTable(input: InputStream, mapping: FoodCompositionCsvMapping): CsvParseResult {
        val csvRows = CSVReader(InputStreamReader(input, StandardCharsets.UTF_8)).iterator().asSequence().drop(mapping.rowOffset)

        val parsedRows = mutableListOf<FoodCompositionTableRecord>()
        val warnings = mutableListOf<String>()

        csvRows.forEachIndexed { index, row ->
            val parsedRow = parseRow(row, index + 2, mapping)
            parsedRows.add(parsedRow.first)
            warnings.addAll(parsedRow.second)
        }

        return CsvParseResult(parsedRows, warnings)
    }
}
