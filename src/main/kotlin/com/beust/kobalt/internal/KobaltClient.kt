package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.kotlin.ScriptCompiler2
import com.beust.kobalt.mainNoExit
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket
import java.util.concurrent.Executors

public class KobaltClient @Inject constructor() : Runnable {
    var outgoing: PrintWriter? = null

    override fun run() {
        val portNumber = 1234

        var done = false
        var attempts = 1
        while (attempts < 3 && ! done) {
            try {
                val socket = Socket("localhost", portNumber)
                outgoing = PrintWriter(socket.outputStream, true)
                val c : String = "{ \"name\":\"GetDependencies\", \"buildFile\": \"c:\\\\users\\\\cbeust\\\\java\\\\testng\\\\Build.kt\"}"
                outgoing!!.println(c)
                done = true
                val ins = BufferedReader(InputStreamReader(socket.inputStream))
                var fromServer = ins.readLine()
                while (fromServer != null) {
                    log(1, "Response from server:\n" + fromServer)
                    fromServer = ins.readLine()
                }
//                done = true
//                log(1, "Launching listening server")
//                while (fromServer != null) {
//                    log(1, "From server: " + fromServer);
//                    if (fromServer.equals("Bye."))
//                        break;
//                    fromServer = ins.readLine()
//                }
            } catch(ex: ConnectException) {
                log(1, "Server not up, sleeping a bit")
                Thread.sleep(2000)
                attempts++
            }
        }
    }

    fun sendInfo(info: ScriptCompiler2.BuildScriptInfo) {
        outgoing!!.println("Sending info with project count: " + info.projects.size())
    }
}
