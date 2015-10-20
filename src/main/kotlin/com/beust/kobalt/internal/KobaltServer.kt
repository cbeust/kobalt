package com.beust.kobalt.internal

import com.beust.klaxon.json
import com.beust.kobalt.Args
import com.beust.kobalt.api.Project
import com.beust.kobalt.kotlin.BuildFile
import com.beust.kobalt.kotlin.ScriptCompiler2
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.MavenDependency
import com.beust.kobalt.maven.SimpleDep
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.nio.file.Paths
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

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
            if (command != null) {
                command!!.run()
            }
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

    private fun toJson(info: ScriptCompiler2.BuildScriptInfo) : String {
        log(1, "Returning JSON for BuildScriptInfo")
        return toJson(info, executors.miscExecutor)
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

    companion object {
        internal fun toJson(info: ScriptCompiler2.BuildScriptInfo, executor: ExecutorService): String {
            val result = "{ projects: [" +
                    info.projects.map { toJson(it, executor) }.join(",\n") +
                    "]\n}\n"
            return result
        }

        private fun toJson(project: Project, executor: ExecutorService): String {
            var result = "{\n" +
                arrayListOf(
                        "\"name\" : \"${project.name}\"",
                        toJson("dependencies", project.compileDependencies, executor),
                        toJson("providedDependencies", project.compileProvidedDependencies, executor),
                        toJson("runtimeDependencies", project.compileRuntimeDependencies, executor),
                        toJson("testDependencies", project.testDependencies, executor),
                        toJson("testProvidedDependencies", project.testProvidedDependencies, executor)
                ).join(",\n") +
                "}\n"
            return result
        }

        private fun toJson(name: String, dependencies: List<IClasspathDependency>, executor: ExecutorService) : String {
            return "\"$name\" : [" +
                    dependencies.map {
                        val dep = MavenDependency.create(it.id, executor)
                        val path = dep.jarFile.get()
                        "{\n" +
                                "\"id\" : \"${it.id}\",\n" +
                                "\"path\" : \"$path\"" +
                                "}\n"
                    }.join(",") +
                    "]"
        }
    }
}
