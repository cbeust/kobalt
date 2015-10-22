package com.beust.kobalt.internal.remote

import com.beust.kobalt.Args
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.kotlin.BuildFileCompiler
import com.beust.kobalt.mainNoExit
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Inject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket
import java.nio.file.Paths
import java.util.concurrent.Executors

public class KobaltClient @Inject constructor() : Runnable {
    var outgoing: PrintWriter? = null

    override fun run() {
        val portNumber = 1234

        var done = false
        var attempts = 1
        while (attempts < 10 && ! done) {
            try {
                val socket = Socket("localhost", portNumber)
                outgoing = PrintWriter(socket.outputStream, true)
                val testBuildfile = Paths.get(SystemProperties.homeDir, "java", "testng", "Build.kt")
                    .toFile().absolutePath
                val testBuildfile2 = Paths.get(SystemProperties.homeDir, "java", "jcommander", "Build.kt")
                        .toFile().absolutePath
                val c : String = "{ \"name\":\"GetDependencies\", \"buildFile\": \"$testBuildfile\"}"
                outgoing!!.println(c)
                val ins = BufferedReader(InputStreamReader(socket.inputStream))
                var line = ins.readLine()
                while (! done && line != null) {
                    log(1, "Received from server:\n" + line)
                    val jo = JsonParser().parse(line) as JsonObject
                    if (jo.has("name") && "Quit" == jo.get("name").asString) {
                        log(1, "Quitting")
//                        outgoing!!.println("{ \"name\": \"Quit\" }")
                        done = true
                    } else {
                        val data = jo.get("data").asString
                        val dd = Gson().fromJson(data, GetDependenciesData::class.java)
                        println("Read GetDependencyData, project count: ${dd.projects.size()}")
                        line = ins.readLine()
                    }
                }
            } catch(ex: ConnectException) {
                log(1, "Server not up, sleeping a bit")
                Thread.sleep(2000)
                attempts++
            }
        }
    }

    fun sendInfo(info: BuildFileCompiler.BuildScriptInfo) {
        outgoing!!.println("Sending info with project count: " + info.projects.size())
    }
}
