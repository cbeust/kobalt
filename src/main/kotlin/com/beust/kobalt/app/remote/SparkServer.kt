package com.beust.kobalt.app.remote

import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.app.Templates
import com.beust.kobalt.internal.PluginInfo
import com.google.common.collect.ListMultimap
import com.google.gson.Gson
import org.slf4j.Logger
import spark.ResponseTransformer
import spark.Route
import spark.Spark
import java.util.concurrent.Executors

class SparkServer(val cleanUpCallback: () -> Unit, val pluginInfo : PluginInfo) : KobaltServer.IServer {

    companion object {
        lateinit var cleanUpCallback: () -> Unit
        val URL_QUIT = "/quit"
        lateinit var watchDog: WatchDog
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

    val log: Logger = org.slf4j.LoggerFactory.getLogger("SparkServer")

    override fun run(port: Int) {
        val threadPool = Executors.newFixedThreadPool(2)
        watchDog = WatchDog(port, 60 * 10 /* 10 minutes */, log)
        threadPool.submit {
            watchDog.run()
        }
        log.debug("Server running")
        Spark.port(port)
        Spark.webSocket("/v1/getDependencyGraph", GetDependencyGraphHandler::class.java)
        Spark.get("/ping") { req, res ->
            watchDog.rearm()
            log.debug("  Received ping")
            """ { "result" : "ok" } """
        }
        Spark.get(URL_QUIT, { req, res ->
            log.debug("  Received quit")
            threadPool.let { executor ->
                executor.submit {
                    Thread.sleep(1000)
                    Spark.stop()
                    executor.shutdown()
                }
                KobaltServer.OK
            }
        })

        jsonRoute("/v0/getTemplates", Route { request, response ->
            TemplatesData.create(Templates().getTemplates(pluginInfo))
        })
        Spark.init()
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

