package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.kotlin.ScriptCompiler2
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket

public class KobaltServer @Inject constructor() : Runnable {
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
            outgoing!!.println("Sending info with project count: " + info.projects.size())
        } else {
            log(1, "Queuing $info")
            synchronized(pending) {
                pending.add(info)
            }
        }
    }
}
