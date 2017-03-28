package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.MainModule
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.build.BuildSources
import com.google.gson.Gson
import java.io.File

//enum class Command(val n: Int, val command: ICommand) {
//    GET_DEPENDENCIES(1, Kobalt.INJECTOR.getInstance(GetDependenciesCommand::class.java)),
//    GET_DEPENDENCIES_GRAPH(2, Kobalt.INJECTOR.getInstance(GetDependenciesGraphCommand::class.java));
//    companion object {
//        val commandMap = hashMapOf<Int, ICommand>()
//        fun commandFor(n: Int) = values().filter { it.n == n }[0].command
//    }
//}

class KobaltHub(val dependencyData: RemoteDependencyData) {
    val args = Args()

    fun runCommand(n: Int) : String {
        val buildSources = BuildSources(File(homeDir("kotlin/klaxon")))
        val data =
            when(n) {
                1 -> Gson().toJson(
                        dependencyData.dependenciesDataFor(buildSources, args))
                2 -> Gson().toJson(
                        dependencyData.dependenciesDataFor(buildSources, args,
                                useGraph = true))
                        else -> throw RuntimeException("Unknown command")
            }
        println("Data: $data")
        return data
    }
}

fun main(argv: Array<String>) {
    Kobalt.init(MainModule(Args(), KobaltSettings.readSettingsXml()))
    val dependencyData = Kobalt.INJECTOR.getInstance(RemoteDependencyData::class.java)
    val json = KobaltHub(dependencyData).runCommand(1)
    val dd = Gson().fromJson(json, RemoteDependencyData.GetDependenciesData::class.java)
    println("Data2: $dd")
}
