package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.kotlin.BuildFile
import com.beust.kobalt.kotlin.ScriptCompiler2
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.MavenDependency
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.inject.Inject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.nio.file.Paths

public class KobaltServer @Inject constructor(val args: Args, val executors: KobaltExecutors,
        val buildFileCompilerFactory: ScriptCompiler2.IFactory) : Runnable {
    var outgoing: PrintWriter? = null
    val pending = arrayListOf<String>()

    override fun run() {
        val portNumber = args.port

        log(1, "Starting on port $portNumber")
        val serverSocket = ServerSocket(portNumber)
        val clientSocket = serverSocket.accept()
        outgoing = PrintWriter(clientSocket.outputStream, true)
        if (pending.size() > 0) {
            log(1, "Emptying the queue, size $pending.size()")
            synchronized(pending) {
                pending.forEach { sendInfo(it) }
                pending.clear()
            }
        }
        val ins = BufferedReader(InputStreamReader(clientSocket.inputStream))
        var inputLine = ins.readLine()
        while (inputLine != null) {
            log(1, "Received $inputLine")
            val command = getCommand(inputLine)
            command?.run()
            if (inputLine.equals("Bye."))
                break;
            inputLine = ins.readLine()
        }
    }

    interface Command {
        fun run()
    }

    inner class PingCommand(val s: String) : Command {
        override fun run() = sendInfo("{ \"response\" : \"$s\" }")
    }

    inner class GetDependenciesCommand(val s: String) : Command {
        override fun run() {
            val buildFile = BuildFile(Paths.get("c:\\Users\\cbeust\\java\\jcommander\\Build.kt"), "JCommander build")
            val scriptCompiler = buildFileCompilerFactory.create(listOf(buildFile))
            scriptCompiler.observable.subscribe {
                info -> sendInfo(toJson(info))
            }
            scriptCompiler.compileBuildFiles(args)
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
        log(1, "Returning JSON for BuildScriptInfo")
        val result = Gson().toJson(GetDependenciesData(projects))
        return result
    }

    private fun getCommand(command: String): Command? {
        if (command == "g") {
            return GetDependenciesCommand(command)
        } else {
            return PingCommand(command)
        }
    }

    fun sendInfo(info: String) {
        if (outgoing != null) {
//            val json = toJson(info, executors.miscExecutor)
            outgoing!!.println(info)
        } else {
            log(1, "Queuing $info")
            synchronized(pending) {
                pending.add(info)
            }
        }
    }
}
