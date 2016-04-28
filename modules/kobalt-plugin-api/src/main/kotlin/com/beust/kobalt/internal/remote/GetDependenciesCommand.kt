package com.beust.kobalt.internal.remote

import com.beust.kobalt.Constants
import java.io.PrintWriter
import java.net.Socket

fun main(argv: Array<String>) {
    Socket("localhost", 1234).use { socket ->
        (PrintWriter(socket.outputStream, true)).use { out ->
            out.println("""{ "name" : "getDependencies", "buildFile":
            "/Users/beust/kotlin/kobalt/kobalt/src/${Constants.BUILD_FILE_NAME}"}""")
        }
    }
}