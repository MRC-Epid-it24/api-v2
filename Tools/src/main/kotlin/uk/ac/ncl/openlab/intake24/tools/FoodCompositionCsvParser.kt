package uk.ac.ncl.openlab.intake24.tools

import com.opencsv.CSVReader
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class FoodCompositionCsvParseException (message: String): Exception(message)

object FoodCompositionCsvParser {

    val logger = LoggerFactory.getLogger(FoodCompositionCsvParser::class.java)

    fun parseTable(input: InputStream, mapping: FoodCompositionCsvMapping): List<Object> {

        val rows = CSVReader(InputStreamReader(input, StandardCharsets.UTF_8)).iterator().asSequence().drop(mapping.rowOffset)

        



        repeat(mapping.rowOffset) {
            if (!iter.hasNext())
                throw FoodCompositionCsvParseException("Unexpected end of file while trying to skip header rows")
            iter.next()
        }

        iter.forEachRemaining {

        }



        def readNutrients (row: IndexedSeq[String], rowIndex: Int): Map[Long, Double] = mapping.nutrientMapping.foldLeft(Map[Long, Double]()) {
            case(acc, (nutrientId, colNum)) => {
            try {
                acc + (nutrientId -> row(colNum-1).toDouble)
            } catch {
                case e : Throwable => {
                    val cell = s"${row(mapping.descriptionColumn)} (row ${rowIndex+1}, column ${offsetToExcelColumn(colNum-1).reverse})"

                    if (nutrientId == 1l)
                        println(s"Failed to read energy (kcal) for $cell! This is an essential nutrient column, please check the source table for errors.")
                    else
                        println(s"Failed to read nutrient type ${nutrientId.toString} for $cell, assuming data N/A")
                    acc
                }
            }
        }
        }

        rows.zipWithIndex.drop(mapping.rowOffset).map {
            case(row, index) =>
            NutrientTableRecord(row(mapping.idColumn), row(mapping.descriptionColumn), mapping.localDescriptionColumn.map(row(_)), readNutrients(row, index))
        }
    }
}
