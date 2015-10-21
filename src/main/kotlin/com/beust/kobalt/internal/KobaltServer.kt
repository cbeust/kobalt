package com.beust.kobalt.internal

import com.beust.klaxon.json
import com.beust.kobalt.Args
import com.beust.kobalt.kotlin.BuildFile
import com.beust.kobalt.kotlin.ScriptCompiler2
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.MavenDependency
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Inject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.SocketException
import java.nio.file.Paths

public class KobaltServer @Inject constructor(val args: Args, val executors: KobaltExecutors,
        val buildFileCompilerFactory: ScriptCompiler2.IFactory) : Runnable {
    var outgoing: PrintWriter? = null
    val pending = arrayListOf<String>()

    override fun run() {
        val portNumber = args.port

        log(1, "Starting on port $portNumber")
        var quit = false
        val serverSocket = ServerSocket(portNumber)
        while (! quit) {
            val clientSocket = serverSocket.accept()
            outgoing = PrintWriter(clientSocket.outputStream, true)
            if (pending.size() > 0) {
                log(1, "Emptying the queue, size $pending.size()")
                synchronized(pending) {
                    pending.forEach { sendData(it) }
                    pending.clear()
                }
            }
            val ins = BufferedReader(InputStreamReader(clientSocket.inputStream))
            try {
                var line = ins.readLine()
                while (!quit && line != null) {
                    log(1, "Received from client $line")
                    val jo = JsonParser().parse(line) as JsonObject
                    if ("Quit" == jo.get("name").asString) {
                        log(1, "Quitting")
                        quit = true
                    } else {
                        runCommand(jo)
                        line = ins.readLine()
                    }
                }
            } catch(ex: SocketException) {
                log(1, "Client disconnected, resetting")
            }
        }
    }

    interface Command {
        fun run(jo: JsonObject)
    }

    inner class PingCommand() : Command {
        override fun run(jo: JsonObject) = sendData("{ \"response\" : \"${jo.toString()}\" }")
    }

    inner class GetDependenciesCommand() : Command {
        override fun run(jo: JsonObject) {
            val buildFile = BuildFile(Paths.get(jo.get("buildFile").asString), "GetDependenciesCommand")
            val scriptCompiler = buildFileCompilerFactory.create(listOf(buildFile))
            scriptCompiler.observable.subscribe {
                buildScriptInfo -> sendData(toJson(buildScriptInfo))
            }
            scriptCompiler.compileBuildFiles(args)
            sendData("{ \"name\": \"Quit\" }")
        }
    }

    class DependencyData(val id: String, val path: String)

    class ProjectData( val name: String, val dependencies: List<DependencyData>,
            val providedDependencies: List<DependencyData>,
            val runtimeDependencies: List<DependencyData>,
            val testDependencies: List<DependencyData>,
            val testProvidedDependencies: List<DependencyData>)

    class GetDependenciesData(val projects: List<ProjectData>)

    private fun toJson(info: ScriptCompiler2.BuildScriptInfo) : String {
        val executor = executors.miscExecutor
        val projects = arrayListOf<ProjectData>()

        fun toDependencyData(d: IClasspathDependency) : DependencyData {
            val dep = MavenDependency.create(d.id, executor)
            return DependencyData(d.id, dep.jarFile.get().absolutePath)
        }

        info.projects.forEach { project ->
            projects.add(ProjectData(project.name!!,
                    project.compileDependencies.map { toDependencyData(it) },
                    project.compileProvidedDependencies.map { toDependencyData(it) },
                    project.compileRuntimeDependencies.map { toDependencyData(it) },
                    project.testDependencies.map { toDependencyData(it) },
                    project.testProvidedDependencies.map { toDependencyData(it) }))
        }
        log(1, "Returning BuildScriptInfo")
        val result = Gson().toJson(GetDependenciesData(projects))
        log(2, "  $result")
        return result
    }

    private val COMMANDS = hashMapOf<String, Command>(
            Pair("GetDependencies", GetDependenciesCommand())
    )

    private fun runCommand(jo: JsonObject) {
        val command = jo.get("name").asString
        if (command != null) {
            COMMANDS.getOrElse(command, { PingCommand() }).run(jo)
        } else {
            error("Did not find a name in command: $jo")
        }
    }

    fun sendData(info: String) {
        if (outgoing != null) {
            outgoing!!.println(info)
        } else {
            log(1, "Queuing $info")
            synchronized(pending) {
                pending.add(info)
            }
        }
    }
}


