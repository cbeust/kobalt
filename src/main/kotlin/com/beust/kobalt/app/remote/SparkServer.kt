package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.google.gson.Gson
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

    override fun run(port: Int) {
        Spark.port(port)
        Spark.get("/hello", Route { req, res -> "Hello world" })
        Spark.get("/v0/getDependencies", { request, response ->
            val buildFile = request.queryParams("buildFile")
            if (buildFile != null) {
                val projects = initCallback(buildFile)
                val result = try {
                    val dependencyData = Kobalt.INJECTOR.getInstance(DependencyData::class.java)
                    val args = Kobalt.INJECTOR.getInstance(Args::class.java)

                    val dd = dependencyData.dependenciesDataFor(buildFile, args)
                    Gson().toJson(dd)
                } catch(ex: Exception) {
                    "Error: " + ex.message
                } finally {
                    cleanUpCallback()
                }

                result
            } else {
                "error"
            }
        })
    }
}

