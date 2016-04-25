package com.beust.kobalt.app.remote

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.remote.CommandData
import com.beust.kobalt.internal.remote.ICommandSender
import com.beust.kobalt.internal.remote.PingCommand
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.*
import java.lang.management.ManagementFactory
import java.net.ServerSocket
import java.net.SocketException
import java.util.*
import java.util.concurrent.Callable

class KobaltServer(val force: Boolean, val shutdownCallback: () -> Unit) : Callable<Int>, ICommandSender {
//    var outgoing: PrintWriter? = null
    val pending = arrayListOf<CommandData>()

    private val COMMAND_CLASSES = listOf(GetDependenciesCommand::class.java, PingCommand::class.java)
    private val COMMANDS = COMMAND_CLASSES.map {
            Kobalt.INJECTOR.getInstance(it).let { Pair(it.name, it) }
        }.toMap()

    override fun call() : Int {
        val port = ProcessUtil.findAvailablePort()
        try {
            if (createServerFile(port, force)) {
                privateRun(port)
            }
        } catch(ex: Exception) {
            ex.printStackTrace()
        } finally {
            deleteServerFile()
        }
        return port
    }

    val SERVER_FILE = KFiles.joinDir(homeDir(KFiles.KOBALT_DOT_DIR, "kobaltServer.properties"))
    val KEY_PORT = "port"
    val KEY_PID = "pid"

    private fun createServerFile(port: Int, force: Boolean) : Boolean {
        if (File(SERVER_FILE).exists() && ! force) {
            log(1, "Server file $SERVER_FILE already exists, is another server running?")
            return false
        } else {
            val processName = ManagementFactory.getRuntimeMXBean().name
            val pid = processName.split("@")[0]
            Properties().apply {
                put(KEY_PORT, port.toString())
                put(KEY_PID, pid)
            }.store(FileWriter(SERVER_FILE), "")
            log(2, "KobaltServer created $SERVER_FILE")
            return true
        }
    }

    private fun deleteServerFile() {
        log(1, "KobaltServer deleting $SERVER_FILE")
        File(SERVER_FILE).delete()
    }

    lateinit var serverInfo: ServerInfo

    class ServerInfo(val port: Int) {
        lateinit var reader: BufferedReader
        lateinit var writer: PrintWriter
        var serverSocket : ServerSocket? = null

        init {
            reset()
        }

        fun reset() {
            if (serverSocket != null) {
                serverSocket!!.close()
            }
            serverSocket = ServerSocket(port)
            var clientSocket = serverSocket!!.accept()
            reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
            writer = PrintWriter(clientSocket.outputStream, true)
        }
    }

    private fun privateRun(port: Int) {
        log(1, "Listening to port $port")
        var quit = false
        serverInfo = ServerInfo(port)
        while (!quit) {
            if (pending.size > 0) {
                log(1, "Emptying the queue, size $pending.size()")
                synchronized(pending) {
                    pending.forEach { sendData(it) }
                    pending.clear()
                }
            }
            var commandName: String? = null
            try {
                var line = serverInfo.reader.readLine()
                while (!quit && line != null) {
                    log(1, "Received from client $line")
                    val jo = JsonParser().parse(line) as JsonObject
                    commandName = jo.get("name").asString
                    if ("quit" == commandName) {
                        log(1, "Quitting")
                        quit = true
                    } else {
                        runCommand(jo)

                        // Done, send a quit to the client
                        sendData(CommandData("quit", ""))

                        // Clean up all the plug-in actors
                        shutdownCallback()
                        line = serverInfo.reader.readLine()
                    }
                }
                if (line == null) {
                    log(1, "Received null line, resetting the server")
                    serverInfo.reset()
                }
            } catch(ex: SocketException) {
                log(1, "Client disconnected, resetting")
                serverInfo.reset()
            } catch(ex: Throwable) {
                ex.printStackTrace()
                if (commandName != null) {
                    sendData(CommandData(commandName, null, ex.message))
                }
                log(1, "Command failed: ${ex.message}")
            }
        }
    }

    private fun runCommand(jo: JsonObject) {
        val command = jo.get("name").asString
        if (command != null) {
            (COMMANDS[command] ?: COMMANDS["ping"])!!.run(this, jo)
        } else {
            error("Did not find a name in command: $jo")
        }
    }

    override fun sendData(commandData: CommandData) {
        val content = Gson().toJson(commandData)
        if (serverInfo.writer != null) {
            serverInfo.writer!!.println(content)
        } else {
            log(1, "Queuing $content")
            synchronized(pending) {
                pending.add(commandData)
            }
        }
    }
}

