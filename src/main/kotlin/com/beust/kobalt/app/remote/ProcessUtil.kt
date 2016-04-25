package com.beust.kobalt.app.remote

import java.io.IOException
import java.net.Socket

class ProcessUtil {
    companion object {
        fun findAvailablePort(): Int {
            for (i in 1234..65000) {
                if (isPortAvailable(i)) return i
            }
            throw IllegalArgumentException("Couldn't find any port available, something is very wrong")
        }

        private fun isPortAvailable(port: Int): Boolean {
            var s: Socket? = null
            try {
                s = Socket("localhost", port)
                return false
            } catch(ex: IOException) {
                return true
            } finally {
                s?.close()
            }
        }
    }
}