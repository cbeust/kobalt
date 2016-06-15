package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.Templates
import com.beust.kobalt.internal.PluginInfo
import com.google.common.collect.ListMultimap
import com.google.gson.Gson
import org.eclipse.jetty.websocket.api.RemoteEndpoint
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketListener
import spark.ResponseTransformer
import spark.Route
import spark.Spark
import java.util.concurrent.Executors

class SparkServer(val initCallback: (String) -> List<Project>, val cleanUpCallback: () -> Unit,
        val pluginInfo : PluginInfo) : KobaltServer.IServer {

    companion object {
        lateinit var initCallback: (String) -> List<Project>
        lateinit var cleanUpCallback: () -> Unit
    }

    init {
        SparkServer.initCallback = initCallback
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
        log.debug("RUNNING")
        Spark.port(port)
        Spark.webSocket("/v1/getDependencies", GetDependenciesChatHandler::class.java)
        Spark.get("/ping", { req, res -> """ { "result" : "ok" } """ })
        Spark.get("/quit", { req, res ->
            Executors.newFixedThreadPool(1).let { executor ->
                executor.submit {
                    Thread.sleep(1000)
                    Spark.stop()
                    executor.shutdown()
                }
                KobaltServer.OK
            }
        })
        jsonRoute("/v0/getDependencies", Route { request, response ->
            val buildFile = request.queryParams("buildFile")
            initCallback(buildFile)
            val result =
                if (buildFile != null) {
                    try {
                        val dependencyData = Kobalt.INJECTOR.getInstance(DependencyData::class.java)
                        val args = Kobalt.INJECTOR.getInstance(Args::class.java)

                        dependencyData.dependenciesDataFor(buildFile, args)
                    } catch(ex: Exception) {
                        DependencyData.GetDependenciesData(errorMessage = ex.message)
                    } finally {
                        cleanUpCallback()
                    }
                } else {
                    DependencyData.GetDependenciesData(
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

class GetDependenciesChatHandler : WebSocketListener {
    var session: Session? = null

    override fun onWebSocketClose(code: Int, reason: String?) {
        println("ON CLOSE $code reason: $reason")
    }

    override fun onWebSocketError(cause: Throwable?) {
        cause?.printStackTrace()
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

            val result = if (buildFile != null) {
                try {
                    val dependencyData = Kobalt.INJECTOR.getInstance(DependencyData::class.java)
                    val args = Kobalt.INJECTOR.getInstance(Args::class.java)

                    dependencyData.dependenciesDataFor(buildFile, args, object : IProgressListener {
                        override fun onProgress(progress: Int?, message: String?) {
                            sendWebsocketCommand(s.remote, ProgressCommand.NAME, ProgressCommand(progress, message))
                        }
                    })
                } catch(ex: Exception) {
                    DependencyData.GetDependenciesData(errorMessage = ex.message)
                } finally {
                    SparkServer.cleanUpCallback()
                }
            } else {
                DependencyData.GetDependenciesData(
                        errorMessage = "buildFile wasn't passed in the query parameter")
            }
            println("GOT DEPENDENCY DATA: $result")
            sendWebsocketCommand(s.remote, DependencyData.GetDependenciesData.NAME, result)
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

