package uk.ac.ncl.intake24.secureurl

import com.google.inject.Inject
import com.typesafe.config.Config
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.temporal.ChronoUnit


class LocalSecureURLCleanupDaemon @Inject() constructor(config: Config) {

    private val logger = LoggerFactory.getLogger(LocalSecureURLCleanupDaemon::class.java)

    private val dirPath = Paths.get(config.getString("secureURL.local.directory"))
    private val validityPeriod = config.getDuration("secureURL.local.validityPeriod")
    private val cleanupInterval = config.getDuration("secureURL.local.cleanupInterval")

    init {
        if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {

            GlobalScope.launch {
                while (true) {
                    val minCreatedAt = Instant.now().minus(validityPeriod)

                    logger.debug("Deleting files created before $minCreatedAt")

                    var stream: DirectoryStream<Path>? = null

                    try {
                        stream = Files.newDirectoryStream(dirPath) { entry -> Files.isRegularFile(entry) }
                        stream.forEach { file ->
                            val attrs = Files.readAttributes(file, BasicFileAttributes::class.java)
                            val createdAt = attrs.creationTime().toInstant()

                            if (createdAt.isBefore(minCreatedAt)) {
                                logger.debug("Deleting $file")
                                Files.delete(file)
                            }
                        }
                    } finally {
                        stream?.close()
                    }

                    delay(cleanupInterval.toMillis())
                }
            }
        } else {
            logger.warn("Directory path does not point to a directory, file cleanup service will not start")
        }
    }
}
