package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.MainModule
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Guice
import java.io.*
import java.net.ConnectException
import java.net.Socket
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Inject

fun main(argv: Array<String>) {
    Kobalt.INJECTOR = Guice.createInjector(MainModule(Args(), KobaltSettings.readSettingsXml()))
    val port = ServerProcess().launch()
    println("SERVER RUNNING ON PORT $port")
}

class ServerProcess {
    val SERVER_FILE = KFiles.joinDir(homeDir(KFiles.KOBALT_DOT_DIR, "kobaltServer.properties"))
    val KEY_PORT = "port"
    val executor = Executors.newFixedThreadPool(5)

    fun launch() : Int {
        var port = launchPrivate()
        while (port == 0) {
            executor.submit {
                KobaltServer(force = true, shutdownCallback = {}).call()
            }
            //            launchServer(ProcessUtil.findAvailablePort())
            port = launchPrivate()
        }
        return port
    }

    private fun launchPrivate() : Int {
        var result = 0
        File(SERVER_FILE).let { serverFile ->
            if (serverFile.exists()) {
                val properties = Properties().apply {
                    load(FileReader(serverFile))
                }

                try {
                    val socket = Socket("localhost", result)
                    val outgoing = PrintWriter(socket.outputStream, true)
                    val c: String = """{ "name": "ping"}"""
                    outgoing.println(c)
                    val ins = BufferedReader(InputStreamReader(socket.inputStream))
                    var line = ins.readLine()
                    val jo = JsonParser().parse(line) as JsonObject
                    val jsonData = jo["data"]?.asString
                    val dataObject = JsonParser().parse(jsonData) as JsonObject
                    val received = JsonParser().parse(dataObject["received"].asString) as JsonObject
                    if (received["name"].asString == "ping") {
                        result = properties.getProperty(KEY_PORT).toInt()
                    }
                } catch(ex: IOException) {
                    log(1, "Couldn't connect to current server, launching a new one")
                    Thread.sleep(1000)
                }
            }
        }

        return result
    }

        private fun launchServer(port: Int) {
            val kobaltJar = File(KFiles().kobaltJar[0])
            log(1, "Kobalt jar: $kobaltJar")
            if (! kobaltJar.exists()) {
                warn("Can't find the jar file " + kobaltJar.absolutePath + " can't be found")
            } else {
                val args = listOf("java",
                        "-classpath", KFiles().kobaltJar.joinToString(File.pathSeparator),
                        "com.beust.kobalt.MainKt",
                        "--dev", "--server", "--port", port.toString())
                val pb = ProcessBuilder(args)
//                pb.directory(File(directory))
                pb.inheritIO()
//                pb.environment().put("JAVA_HOME", ProjectJdkTable.getInstance().allJdks[0].homePath)
                val tempFile = createTempFile("kobalt")
                pb.redirectOutput(tempFile)
                warn("Launching " + args.joinToString(" "))
                warn("Server output in: $tempFile")
                val process = pb.start()
                val errorCode = process.waitFor()
                if (errorCode == 0) {
                    log(1, "Server exiting")
                } else {
                    log(1, "Server exiting with error")
                }
            }
        }

    private fun createServerFile(port: Int, force: Boolean) : Boolean {
        if (File(SERVER_FILE).exists() && ! force) {
            log(1, "Server file $SERVER_FILE already exists, is another server running?")
            return false
        } else {
            Properties().apply {
                put(KEY_PORT, port.toString())
            }.store(FileWriter(SERVER_FILE), "")
            log(2, "KobaltServer created $SERVER_FILE")
            return true
        }
    }

    private fun deleteServerFile() {
        log(1, "KobaltServer deleting $SERVER_FILE")
        File(SERVER_FILE).delete()
    }
}

class KobaltClient @Inject constructor() : Runnable {
    var outgoing: PrintWriter? = null

    override fun run() {
        val portNumber = 1234

        var done = false
        var attempts = 1
        while (attempts < 10 && ! done) {
            try {
                val socket = Socket("localhost", portNumber)
                outgoing = PrintWriter(socket.outputStream, true)
                val testBuildfile = Paths.get(SystemProperties.homeDir, "kotlin/klaxon/kobalt/src/Build.kt")
                    .toFile().absolutePath
                val c : String = """{ "name": "getDependencies", "buildFile": "$testBuildfile"}"""
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
                        val dd = Gson().fromJson(data, DependencyData.GetDependenciesData::class.java)
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
