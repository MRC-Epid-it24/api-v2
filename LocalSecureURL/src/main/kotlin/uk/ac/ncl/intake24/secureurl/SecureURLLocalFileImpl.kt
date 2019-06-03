package uk.ac.ncl.intake24.secureurl

import com.google.inject.Inject
import com.google.inject.Singleton
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

@Singleton
class SecureURLLocalFileImpl @Inject() constructor(val config: Config) : SecureURLService {

    private val logger = LoggerFactory.getLogger(SecureURLLocalFileImpl::class.java)

    private val dirPath = Paths.get(config.getString("secureURL.local.directory"))
    private val downloadURLPrefix = config.getString("secureURL.local.downloadURLPrefix")


    init {
        if (Files.exists(dirPath) && !Files.isDirectory(dirPath))
            logger.error("$dirPath is not a directory. This will cause export errors. Please check the configuration files.")
        else if (!Files.exists(dirPath)) {
            logger.warn("$dirPath does not exist. Attempting to create.")
            Files.createDirectories(dirPath)
        }
    }

    override fun createURL(fileName: String, file: Path): URL {
        val secureName = UUID.randomUUID().toString()
        val destPath = dirPath.resolve("$secureName.$fileName")
        Files.move(file, destPath)
        return URL("$downloadURLPrefix/download?key=$secureName")
    }
}
