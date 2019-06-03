package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import com.typesafe.config.Config
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths

class LocalSecureURLController @Inject constructor(private val config: Config) {

    private val directory = Paths.get(config.getString("secureURL.local.directory"))

    fun download(request: Request): Response {

        val key = request.query("key")

        if (key == null)
            return Response(Status.BAD_REQUEST)
        else {
            val file = Files.newDirectoryStream(directory).find { it.getFileName().toString().startsWith(key) }

            if (file == null)
                return Response(Status.NOT_FOUND)
            else {
                val clientName = file.fileName.toString().drop(key.length + 1)
                return Response(Status.OK)
                        .header("Content-Type", "text/csv")
                        .header("Content-Disposition", "attachment; filename=\"$clientName\"")
                        .body(FileInputStream(file.toFile()))
            }

        }
    }
}
