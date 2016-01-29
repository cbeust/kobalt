package com.beust.kobalt.app.remote

import com.beust.kobalt.SystemProperties
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket
import java.nio.file.Paths
import javax.inject.Inject

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
                val testBuildfile = Paths.get(SystemProperties.homeDir, "java/testng/kobalt/src/Build.kt")
                    .toFile().absolutePath
                val c : String = "{ \"name\":\"getDependencies\", \"buildFile\": \"$testBuildfile\"}"
                outgoing!!.println(c)
                val ins = BufferedReader(InputStreamReader(socket.inputStream))
                var line = ins.readLine()
                while (! done && line != null) {
                    log(1, "Received from server:\n" + line)
                    val jo = JsonParser().parse(line) as JsonObject
                    if (jo.has("name") && "quit" == jo.get("name").asString.toLowerCase()) {
                        log(1, "Quitting")
//                        outgoing!!.println("{ \"name\": \"Quit\" }")
                        done = true
                    } else {
                        val data = jo.get("data").asString
                        val dd = Gson().fromJson(data, GetDependenciesCommand.GetDependenciesData::class.java)
                        println("Read GetDependencyData, project count: ${dd.projects.size}")
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
}
