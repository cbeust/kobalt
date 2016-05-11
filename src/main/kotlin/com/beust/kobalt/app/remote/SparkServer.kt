package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.Templates
import com.beust.kobalt.internal.PluginInfo
import com.google.common.collect.ListMultimap
import com.google.gson.Gson
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

    override fun run(port: Int) {
        Spark.port(port)
        Spark.get("/ping", { req, res -> KobaltServer.OK })
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
                        DependencyData.GetDependenciesData(emptyList<DependencyData.ProjectData>(), ex.message)
                    } finally {
                        cleanUpCallback()
                    }
                } else {
                    DependencyData.GetDependenciesData(emptyList<DependencyData.ProjectData>(),
                            "buildFile wasn't passed in the query parameter")
                }
            cleanUpCallback()
            result
        })
        jsonRoute("/v0/getTemplates", Route { request, response ->
            TemplatesInfo.create(Templates().getTemplates(pluginInfo))
        })
    }
}

class TemplateInfo(val pluginName: String, val templates: List<String>)

class TemplatesInfo(val templates: List<TemplateInfo>) {
    companion object {
        fun create(map: ListMultimap<String, ITemplate>) : TemplatesInfo {
            val templateList = arrayListOf<TemplateInfo>()
            map.keySet().forEach { pluginName ->
                val list = map[pluginName].map { it.templateName }
                templateList.add(TemplateInfo(pluginName, list))
            }
            return TemplatesInfo(templateList)
        }
    }
}

