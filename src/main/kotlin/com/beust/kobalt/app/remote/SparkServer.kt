package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.google.gson.Gson
import spark.ResponseTransformer
import spark.Route
import spark.Spark

class SparkServer(val initCallback: (String) -> List<Project>, val cleanUpCallback: () -> Unit)
        : KobaltServer .IServer {

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

    override fun run(port: Int) {
        Spark.port(port)
        Spark.get("/ping", { req, res -> "The Kobalt server is up and running" })
        Spark.get("/quit", {
            req, res -> println("Kobalt server quitting...")
            Spark.stop()
        })
        Spark.get("/v0/getDependencies", "application/json", Route { request, response ->
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
        }, JsonTransformer())
    }
}

