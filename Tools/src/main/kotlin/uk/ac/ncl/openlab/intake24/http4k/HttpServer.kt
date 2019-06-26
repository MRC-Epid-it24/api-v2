package uk.ac.ncl.openlab.intake24.http4k

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.google.inject.*
import com.google.inject.name.Names
import com.sun.security.ntlm.Server
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import org.http4k.core.*
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.*
import org.http4k.server.Netty
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.exception.NoDataFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.tools.TaskStatusManager
import java.lang.Exception
import java.net.InetSocketAddress
import java.time.OffsetDateTime

data class NettyConfig(val host: String, val port: Int) : ServerConfig {
    override fun toServer(httpHandler: HttpHandler): Http4kServer = object : Http4kServer {
        private val masterGroup = NioEventLoopGroup()
        private val workerGroup = NioEventLoopGroup()
        private var closeFuture: ChannelFuture? = null
        private lateinit var address: InetSocketAddress

        override fun start(): Http4kServer = apply {
            val bootstrap = ServerBootstrap()
            bootstrap.group(masterGroup, workerGroup)
                    .channelFactory(ChannelFactory<ServerChannel> { NioServerSocketChannel() })
                    .childHandler(object : ChannelInitializer<SocketChannel>() {
                        public override fun initChannel(ch: SocketChannel) {
                            ch.pipeline().addLast("codec", HttpServerCodec())
                            ch.pipeline().addLast("aggregator", HttpObjectAggregator(Int.MAX_VALUE))
                            ch.pipeline().addLast("handler", Http4kChannelHandler(httpHandler))
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1000)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)

            val channel = bootstrap.bind(host, port).sync().channel()
            address = channel.localAddress() as InetSocketAddress
            closeFuture = channel.closeFuture()
        }

        override fun stop() = apply {
            closeFuture?.cancel(false)
            workerGroup.shutdownGracefully()
            masterGroup.shutdownGracefully()
        }

        override fun port(): Int = if (port > 0) 0 else address.port
    }
}

class TaskStatusController @Inject() constructor(private val taskStatusManager: TaskStatusManager,
                                                 private val stringCodec: StringCodec) {

    fun getTasksList(user: Intake24User, request: Request): Response {
        val type = request.query("type")

        return if (type == null)
            Response(Status.BAD_REQUEST).body("\"type\" query parameter missing")
        else {
            val responseBody =
                    stringCodec.encode(taskStatusManager.getTaskList(user.userId, type, OffsetDateTime.now().minusDays(1)))

            Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(responseBody)
        }
    }
}

object DefaultResponseHeaders : Filter {

    private const val contentType = "content-type"

    override fun invoke(next: HttpHandler): HttpHandler {
        return {
            val response = next.invoke(it)

            if (response.header(contentType) == null)
                response.header(contentType, ContentType.APPLICATION_JSON.toHeaderValue())
            else
                response
        }
    }
}

@Singleton
class UnhandledExceptionHandler @Inject() constructor(val errorUtils: ErrorUtils) : Filter {

    private val logger = LoggerFactory.getLogger(UnhandledExceptionHandler::class.java)!!

    override fun invoke(next: HttpHandler): HttpHandler {
        return {
            try {
                next.invoke(it)
            } catch (e: Exception) {
                logger.error("Unhandled ${e.javaClass.name}", e)
                errorUtils.errorResponse(Status.INTERNAL_SERVER_ERROR, e)
            }
        }
    }
}

@Singleton
class CommonExceptionHandler @Inject() constructor(val errorUtils: ErrorUtils) : Filter {

    private val logger = LoggerFactory.getLogger(CommonExceptionHandler::class.java)!!

    override fun invoke(next: HttpHandler): HttpHandler {
        return {
            try {
                next.invoke(it)
            } catch (e: NoDataFoundException) {
                // This is typically not an error worth logging
                errorUtils.errorResponse(Status.NOT_FOUND, "Record not found")
            } catch (e: DataAccessException) {
                logger.error("Database error", e)
                errorUtils.errorResponse(Status.INTERNAL_SERVER_ERROR, e)
            } catch (e: JsonMappingException) {
                errorUtils.errorResponse(Status.BAD_REQUEST, e)
            } catch (e: JsonParseException) {
                errorUtils.errorResponse(Status.BAD_REQUEST, e)
            }
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

    val fctController = injector.getInstance(FoodCompositionTableController::class.java)

    val unhandledExceptionHandler = injector.getInstance(UnhandledExceptionHandler::class.java)

    val commonExceptionHandler = injector.getInstance(CommonExceptionHandler::class.java)

    val router = routes(
            "/foods/frequencies" bind Method.POST to authenticate(restrictToRoles(listOf("superuser"), exportController::exportFrequencies)),
            "/tasks" bind Method.GET to authenticate(taskStatusController::getTasksList),
            "/foods/composition/tables" bind Method.GET to authenticate(fctController::getCompositionTables),
            "/foods/composition/tables" bind Method.POST to authenticate(fctController::createCompositionTable),
            "/foods/composition/tables/{tableId}" bind Method.GET to authenticate(fctController::getCompositionTable),
            "/foods/composition/tables/{tableId}/csv" bind Method.PATCH to authenticate(fctController::uploadCsv),
            "/foods/composition/tables/{tableId}" bind Method.PATCH to authenticate(fctController::updateCompositionTable),

            "/foods/composition/nutrients" bind Method.GET to authenticate(fctController::getNutrientTypes),
            "/files/download" bind Method.GET to fileDownloadController::download
    )

    val corsPolicy = CorsPolicy(listOf("*"), listOf("X-Auth-Token", "Content-Type"), Method.values().toList())

    val app =
            ServerFilters.Cors(corsPolicy)
                    .then(unhandledExceptionHandler)
                    .then(commonExceptionHandler)
                    .then(DefaultResponseHeaders)
                    .then(router)

    val host = config.getString("http.host")
    val port = config.getInt("http.port")

    app.asServer(NettyConfig(host, port)).start()
}
