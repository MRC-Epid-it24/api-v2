package uk.ac.ncl.openlab.intake24.tools.csvutils

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right

private val EXCEL_COLUMN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()

fun offsetToExcelColumn(offset_: Int): String {
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

fun excelColumnToOffset(columnRef: String): Int {
    var result = 0
    var mul = 1

    for (ch in columnRef.reversed()) {
        result += (ch - 'A' + 1) * mul
        mul *= 26
    }

    return result - 1
}

class SafeRowReader(private val row: Array<String>, private val rowIndex: Int) {

    fun getColumn(index: Int): String? {
        return if (index >= row.size)
            null
        else
            row[index].trim()
    }

    fun getColumn(key: Pair<Int, String>): Either<String, String> {
        return if (key.first >= row.size)
            Left("${key.second} (column ${offsetToExcelColumn(key.first)}) is required in row $rowIndex but the column is missing")
        else {
            val value = row[key.first].trim()

            if (value.isBlank())
                Left("${key.second} (column ${offsetToExcelColumn(key.first)}) is required in row $rowIndex but the column is blank")
            else
                Right(value)
        }
    }

    fun getOneOf(key1: Pair<Int, String>, key2: Pair<Int, String>): Either<String, String> {
        return when {
            key1.first < row.size && row[key1.first].isNotBlank() -> Right(row[key1.first].trim())
            key2.first < row.size && row[key2.first].isNotBlank() -> Right(row[key2.first].trim())
            else -> Left("Either ${key1.second} (column ${offsetToExcelColumn(key1.first)}) or ${key2.second} (column ${offsetToExcelColumn(key2.first)}) is required for row $rowIndex but both are missing")
        }
    }

    fun collectNonBlank(vararg indices: Int): List<String> {
        return indices.map { row[it].trim() }.filter { it.isNotBlank() }
    }

    fun collectNonBlank(indices: Collection<Int>): List<String> {
        return indices.map { row[it].trim() }.filter { it.isNotBlank() }
    }
}
