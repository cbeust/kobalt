package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.api.Project
import com.beust.kobalt.kotlin.ScriptCompiler2
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.MavenDependency
import com.beust.kobalt.maven.SimpleDep
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

public class KobaltServer @Inject constructor(val executors: KobaltExecutors) : Runnable {
    var outgoing: PrintWriter? = null
    val pending = arrayListOf<ScriptCompiler2.BuildScriptInfo>()

    override fun run() {
        val portNumber = Args.DEFAULT_SERVER_PORT

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
            if (inputLine.equals("Bye."))
                break;
            inputLine = ins.readLine()
        }
    }

    fun sendInfo(info: ScriptCompiler2.BuildScriptInfo) {
        if (outgoing != null) {
            val json = toJson(info, executors.miscExecutor)
            outgoing!!.println(json)
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
