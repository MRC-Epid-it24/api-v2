package uk.ac.ncl.intake24.secureurl

import java.net.URL
import java.nio.file.Path

interface SecureURLService {
    fun createURL(fileName: String, file: Path): URL
}