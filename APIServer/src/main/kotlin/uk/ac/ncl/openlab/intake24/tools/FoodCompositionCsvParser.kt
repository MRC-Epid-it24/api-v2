package uk.ac.ncl.openlab.intake24.tools

import com.opencsv.CSVReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets


data class FoodCompositionTableRecord(val recordId: String, val englishDescription: String, val localDescription: String?, val nutrients: List<Pair<Int, Double>>)

data class CsvParseResult(val rows: List<FoodCompositionTableRecord>, val warnings: List<String>)

class CsvParseException(message: String, cause: Throwable) : Exception(message, cause)

object FoodCompositionCsvParser {

    private val EXCEL_COLUMN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()

    private fun offsetToExcelColumn(offset_: Int): String {
        val sb = StringBuilder()
        var offset = offset_

        while (offset > (EXCEL_COLUMN_CHARS.size - 1)) {
            val d = offset / EXCEL_COLUMN_CHARS.size - 1
            val rem = offset % EXCEL_COLUMN_CHARS.size

            sb.append(EXCEL_COLUMN_CHARS[rem])
            offset = d
        }

        sb.append(EXCEL_COLUMN_CHARS[offset])
        sb.reverse()
        return sb.toString()
    }


    private fun parseRow(row: Array<String>, rowIndex: Int, mapping: FoodCompositionCsvMapping, nutrientTypeDescriptions: Map<Int, String>): Pair<FoodCompositionTableRecord, List<String>> {
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
                else {

                    val nutrientDesc = nutrientTypeDescriptions[col.nutrientId]
                            ?: "unknown nutrient type ${col.nutrientId}"
                    val columnRef = offsetToExcelColumn(col.columnOffset)

                    warnings.add("Failed to parse $nutrientDesc in row $rowIndex, column $columnRef: ${e.javaClass.simpleName}: ${e.message
                            ?: ""}")
                }
            }
        }

        return Pair(FoodCompositionTableRecord(recordId, description, localDescription, nutrients), warnings)
    }


    fun parseTable(input: InputStream, mapping: FoodCompositionCsvMapping, nutrientTypeDescriptions: Map<Int, String>): CsvParseResult {
        val csvRows = CSVReader(InputStreamReader(input, StandardCharsets.UTF_8)).iterator().asSequence().drop(mapping.rowOffset)

        val parsedRows = mutableListOf<FoodCompositionTableRecord>()
        val warnings = mutableListOf<String>()

        csvRows.forEachIndexed { index, row ->
            val parsedRow = parseRow(row, index + mapping.rowOffset + 1, mapping, nutrientTypeDescriptions)
            parsedRows.add(parsedRow.first)
            warnings.addAll(parsedRow.second)
        }

        return CsvParseResult(parsedRows, warnings)
    }
}
