package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Inject
import com.google.inject.Module
import com.google.inject.name.Names
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.http4k.core.*
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.jooq.SQLDialect
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.tools.TaskStatusManager


fun pong(request: Request): Response {
    return Response(Status.OK).body("pong!")
}

fun whoami(user: Intake24User, request: Request): Response {
    return Response(Status.OK).body("User id: ${user.userId}, roles: ${user.roles}")
}


class TaskStatusController @Inject() constructor(private val taskStatusManager: TaskStatusManager,
                                                 private val stringCodec: StringCodec) {

    fun getTaskStatus(user: Intake24User, request: Request): Response {
        val id = request.path("id")

        if (id == null) {
            return Response(Status.BAD_REQUEST)
        } else {
            val status = taskStatusManager.getTaskStatus(id)

            if (status == null)
                return Response(Status.NOT_FOUND)
            else
                return Response(Status.OK).body(stringCodec.encode(status))
        }
    }
}

fun main() {

    val config = ConfigFactory.load()

    val systemDatabase = DatabaseClient(
            config.getString("db.system.url"),
            config.getString("db.system.user"),
            config.getString("db.system.password"),
            SQLDialect.POSTGRES_9_5)

    val foodsDatabase = DatabaseClient(
            config.getString("db.foods.url"),
            config.getString("db.foods.user"),
            config.getString("db.foods.password"),
            SQLDialect.POSTGRES_9_5)

    val coreModule = object : AbstractModule() {
        override fun configure() {
            bind(Config::class.java).toInstance(config)
            bind(DatabaseClient::class.java).annotatedWith(Names.named("system")).toInstance(systemDatabase)
            bind(DatabaseClient::class.java).annotatedWith(Names.named("foods")).toInstance(foodsDatabase)
        }
    }

    val modules = config.getStringList("modules").map { moduleName ->
        Class.forName(moduleName).getConstructor().newInstance() as Module
    }

    val injector = Guice.createInjector(modules + coreModule)

    val authenticate = Intake24AuthHandler(config.getString("authentication.jwtSecret"))

    val taskStatusController = injector.getInstance(TaskStatusController::class.java)

    val exportController = injector.getInstance(FoodFrequencyStatsController::class.java)

    val fileDownloadController = injector.getInstance(LocalSecureURLController::class.java)

    val router = routes(
            "/foods/frequencies" bind Method.POST to authenticate(restrictToRoles(listOf("superuser"), exportController::exportFrequencies)),
            "/tasks/{id}/status" bind Method.GET to authenticate(taskStatusController::getTaskStatus),
            "/files/download" bind Method.GET to fileDownloadController::download
    )

    val app: HttpHandler = router

    app.asServer(Netty(8080)).start()
}

