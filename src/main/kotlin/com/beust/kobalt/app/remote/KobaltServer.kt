package com.beust.kobalt.app.remote

import com.beust.kobalt.api.Project
import com.beust.kobalt.homeDir
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.io.FileWriter
import java.lang.management.ManagementFactory
import java.util.*
import java.util.concurrent.Callable

/**
 * Launch a Kobalt server. If @param{force} is specified, a new server will be launched even if one was detected
 * to be already running (from the ~/.kobalt/kobaltServer.properties file).
 *
 * The callbacks are used to initialize and clean up the state before and after each command, so that Kobalt's state
 * can be properly reset, making the server reentrant.
 */
class KobaltServer(val force: Boolean, val givenPort: Int? = null,
        val initCallback: (String) -> List<Project>,
        val cleanUpCallback: () -> Unit) : Callable<Int> {
//    var outgoing: PrintWriter? = null

    interface IServer {
        fun run(port: Int)
    }

    override fun call() : Int {
        val port = givenPort ?: ProcessUtil.findAvailablePort(1234)
        try {
            if (createServerFile(port, force)) {
                log(1, "KobaltServer listening on port $port")
//                OldServer(initCallback, cleanUpCallback).run(port)
//                JerseyServer(initCallback, cleanUpCallback).run(port)
                SparkServer(initCallback, cleanUpCallback).run(port)
//                WasabiServer(initCallback, cleanUpCallback).run(port)
            }
        } catch(ex: Exception) {
            ex.printStackTrace()
        } finally {
//            deleteServerFile()
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
}

