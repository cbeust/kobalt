package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.ProjectFinder
import com.beust.kobalt.app.Templates
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.internal.eventbus.ArtifactDownloadedEvent
import com.beust.kobalt.maven.aether.Exceptions
import com.google.common.collect.ListMultimap
import com.google.common.eventbus.EventBus
import com.google.common.eventbus.Subscribe
import com.google.gson.Gson
import org.eclipse.jetty.websocket.api.RemoteEndpoint
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketListener
import spark.ResponseTransformer
import spark.Route
import spark.Spark
import java.nio.file.Paths
import java.util.concurrent.Executors

class SparkServer(val cleanUpCallback: () -> Unit, val pluginInfo : PluginInfo) : KobaltServer.IServer {

    companion object {
        lateinit var cleanUpCallback: () -> Unit
    }

    init {
        SparkServer.cleanUpCallback = cleanUpCallback
    }

    class JsonTransformer : ResponseTransformer {
        val gson = Gson()
        override fun render(model: Any) = gson.toJson(model)
    }

    private fun jsonRoute(path: String, route: Route)
        = Spark.get(path, "application/json", route, JsonTransformer())

    val log = org.slf4j.LoggerFactory.getLogger("SparkServer")

    override fun run(port: Int) {
        log.debug("Server running")
        Spark.port(port)
        Spark.webSocket("/v1/getDependencies", GetDependenciesHandler::class.java)
        Spark.webSocket("/v1/getDependencyGraph", GetDependencyGraphHandler::class.java)
        Spark.get("/ping") { req, res ->
            log.debug("  Received ping")
            """ { "result" : "ok" } """
        }
        Spark.get("/quit", { req, res ->
            log.debug("  Received quit")
            Executors.newFixedThreadPool(1).let { executor ->
                executor.submit {
                    Thread.sleep(1000)
                    Spark.stop()
                    executor.shutdown()
                }
                KobaltServer.OK
            }
        })

        //
        // The /v0 endpoints are deprecated and will eventually be removed
        // (replaced by /v1 which uses WebSockets
        jsonRoute("/v0/getDependencies", Route { request, response ->
            val buildFile = request.queryParams("buildFile")
            val result =
                if (buildFile != null) {
                    try {
                        val dependencyData = Kobalt.INJECTOR.getInstance(RemoteDependencyData::class.java)
                        val args = Kobalt.INJECTOR.getInstance(Args::class.java)

                        dependencyData.dependenciesDataFor(buildFile, args)
                    } catch(ex: Exception) {
                        RemoteDependencyData.GetDependenciesData(errorMessage = ex.message)
                    } finally {
                        cleanUpCallback()
                    }
                } else {
                    RemoteDependencyData.GetDependenciesData(
                            errorMessage = "buildFile wasn't passed in the query parameter")
                }
            cleanUpCallback()
            result
        })
        jsonRoute("/v0/getTemplates", Route { request, response ->
            TemplatesData.create(Templates().getTemplates(pluginInfo))
        })
        Spark.init()
    }
}

/**
 * Manage the websocket endpoint "/v1/getDependencies".
 */
@Deprecated(message = "Replaced with GetDependencyGraphHandler")
class GetDependenciesHandler : WebSocketListener {
    // The SparkJava project refused to merge https://github.com/perwendel/spark/pull/383
    // so I have to do dependency injections manually :-(
    val projectFinder = Kobalt.INJECTOR.getInstance(ProjectFinder::class.java)

    var session: Session? = null

    override fun onWebSocketClose(code: Int, reason: String?) {
        println("ON CLOSE $code reason: $reason")
    }

    override fun onWebSocketError(cause: Throwable?) {
        Exceptions.printStackTrace(cause!!)
        throw UnsupportedOperationException()
    }

    fun <T> sendWebsocketCommand(endpoint: RemoteEndpoint, commandName: String, payload: T) {
        endpoint.sendString(Gson().toJson(WebSocketCommand(commandName, payload = Gson().toJson(payload))))
    }

    override fun onWebSocketConnect(s: Session) {
        session = s
        val buildFileParams = s.upgradeRequest.parameterMap["buildFile"]
        if (buildFileParams != null) {
            val buildFile = buildFileParams[0]

            fun <T> getInstance(cls: Class<T>) : T = Kobalt.INJECTOR.getInstance(cls)

            val result = if (buildFile != null) {
                // Track all the downloads that this dependency call might trigger and
                // send them as a progress message to the web socket
                val eventBus = getInstance(EventBus::class.java)
                val busListener = object {
                    @Subscribe
                    fun onArtifactDownloaded(event: ArtifactDownloadedEvent) {
                        sendWebsocketCommand(s.remote, ProgressCommand.NAME,
                                ProgressCommand(null, "Downloaded " + event.artifactId))
                    }
                }
                eventBus.register(busListener)

                // Get the dependencies for the requested build file and send progress to the web
                // socket for each project
                try {
                    val dependencyData = getInstance(RemoteDependencyData::class.java)
                    val args = getInstance(Args::class.java)

                    val allProjects = projectFinder.initForBuildFile(BuildFile(Paths.get(buildFile), buildFile),
                            args)

                    dependencyData.dependenciesDataFor(buildFile, args, object : IProgressListener {
                        override fun onProgress(progress: Int?, message: String?) {
                            sendWebsocketCommand(s.remote, ProgressCommand.NAME, ProgressCommand(progress, message))
                        }
                    })
                } catch(ex: Throwable) {
                    ex.printStackTrace()
                    val errorMessage = ex.stackTrace.map { it.toString() }.joinToString("\n<p>")
                    RemoteDependencyData.GetDependenciesData(errorMessage = errorMessage)
                } finally {
                    SparkServer.cleanUpCallback()
                    eventBus.unregister(busListener)
                }
            } else {
                RemoteDependencyData.GetDependenciesData(
                        errorMessage = "buildFile wasn't passed in the query parameter")
            }
            sendWebsocketCommand(s.remote, RemoteDependencyData.GetDependenciesData.NAME, result)
            s.close()
        }
    }

    override fun onWebSocketText(message: String?) {
        println("RECEIVED TEXT: $message")
        session?.remote?.sendString("Response: $message")
    }

    override fun onWebSocketBinary(payload: ByteArray?, offset: Int, len: Int) {
        println("RECEIVED BINARY: $payload")
    }

}

class ProgressCommand(val progress: Int? = null, val message: String? = null) {
    companion object {
        val NAME = "ProgressCommand"
    }
}

class WebSocketCommand(val commandName: String, val errorMessage: String? = null, val payload: String)

class TemplateData(val pluginName: String, val templates: List<String>)

class TemplatesData(val templates: List<TemplateData>) {
    companion object {
        fun create(map: ListMultimap<String, ITemplate>) : TemplatesData {
            val templateList = arrayListOf<TemplateData>()
            map.keySet().forEach { pluginName ->
                val list = map[pluginName].map { it.templateName }
                templateList.add(TemplateData(pluginName, list))
            }
            return TemplatesData(templateList)
        }
    }
}

