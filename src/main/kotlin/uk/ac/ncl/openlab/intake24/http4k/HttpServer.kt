package uk.ac.ncl.openlab.intake24.http4k

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.jooq.SQLDialect
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.tools.FoodFrequencyStatsService

import uk.ac.ncl.openlab.intake24.tools.Intake24User
import uk.ac.ncl.openlab.intake24.tools.TaskStatusManager


fun pong(request: Request): Response {
    return Response(OK).body("pong!")
}

fun whoami(user: Intake24User, request: Request): Response {
    return Response(OK).body("User id: ${user.userId}, roles: ${user.roles}")
}


class ExportController(val foodFrequencyStatsService: FoodFrequencyStatsService) {
    fun exportFrequencies(user: Intake24User, request: Request): Response {
        val id = foodFrequencyStatsService.exportFoodFrequency("en_GB", emptyList())

        Response()

    }
}

fun main() {
    val systemDatabase = DatabaseClient(
            "jdbc:postgresql://192.168.56.2:5432/intake24_foods",
            "intake24",
            "intake24", SQLDialect.POSTGRES_9_5)

    val authenticate = Intake24AuthHandler("zV;3:xvweW]@G5JTK7j;At<;pSj:NM=g[ALNpj?[NiWoUu3jK;K@s^a/LPf8S:5K")

    val taskStatusManager = TaskStatusManager()

    val foodFrequencyStatsService = FoodFrequencyStatsService(systemDatabase, taskStatusManager)

    val router = routes(

            "/foods/frequencies" bind Method.POST to

                    "/ping" bind Method.GET to ::pong,
            "/whoami" bind Method.GET to authenticate(restrictToRoles(listOf("zorboz"), ::whoami)),
            "/greet/{name}" bind Method.GET to { req: Request ->
                val path: String? = req.path("name")
                Response(OK).body("hello ${path ?: "anon!"}")
            }
    )

    val app: HttpHandler = router

    app.asServer(Netty(8080)).start()
}


/*

fun main() {
    // we can bind HttpHandlers (which are just functions from  Request -> Response) to paths/methods to create a Route,
    // then combine many Routes together to make another HttpHandler

    // call the handler in-memory without spinning up a server
    val inMemoryResponse: Response = app(Request(GET, "/greet/Bob"))
    println(inMemoryResponse)

// Produces:
//    HTTP/1.1 200 OK
//
//
//    hello Bob

    // this is a Filter - it performs pre/post processing on a request or response
    val timingFilter = Filter {
        next: HttpHandler ->
        {
            request: Request ->
            val start = System.currentTimeMillis()
            val response = next(request)
            val latency = System.currentTimeMillis() - start
            println("Request to ${request.uri} took ${latency}ms")
            response
        }
    }

    // we can "stack" filters to create reusable units, and then apply them to an HttpHandler
    val compositeFilter = CachingFilters.Response.NoCache().then(timingFilter)
    val filteredApp: HttpHandler = compositeFilter.then(app)

    // only 1 LOC to mount an app and start it in a container
    filteredApp.asServer(Jetty(9000)).start()

    // HTTP clients are also HttpHandlers!
    val client: HttpHandler = OkHttp()

    val networkResponse: Response = client(Request(GET, "http://localhost:9000/greet/Bob"))
    println(networkResponse)

// Produces:
//    Request to /api/greet/Bob took 1ms
//    HTTP/1.1 200
//    cache-control: private, must-revalidate
//    content-length: 9
//    date: Thu, 08 Jun 2017 13:01:13 GMT
//    expires: 0
//    server: Jetty(9.3.16.v20170120)
//
//    hello Bob
}*/