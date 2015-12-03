package com.beust.kobalt.maven

import com.beust.kobalt.HostInfo
import com.beust.kobalt.maven.dependency.FileDependency
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.*

/**
 * Abstracts a URL so that it works transparently on either http:// or file://
 */
class Kurl(val hostInfo: HostInfo) {
//    constructor(url: String) : this(HostInfo(url))

    val connection : URLConnection
        get() {
            val result = URL(hostInfo.url).openConnection()
            if (hostInfo.hasAuth()) {
                val userPass = hostInfo.keyUsername + ":" + hostInfo.keyPassword
                val basicAuth = "Basic " + String(Base64.getEncoder().encode(userPass.toByteArray()))
                result.setRequestProperty("Authorization", basicAuth)
            }
            return result
        }

    val inputStream : InputStream by lazy {
        connection.inputStream
    }

    val exists : Boolean
        get() {
            if (hostInfo.url.contains("localhost")) {
                println("DONOTCOMMIT")
            }
            val url = hostInfo.url
            val result =
                    if (connection is HttpURLConnection) {
                        val responseCode = (connection as HttpURLConnection).responseCode
                        checkResponseCode(responseCode)
                        responseCode == 200
                    } else if (url.startsWith(FileDependency.PREFIX_FILE)) {
                        val fileName = url.substring(FileDependency.PREFIX_FILE.length)
                        File(fileName).exists()
                    } else {
                        false
                    }
            return result
        }

    private fun checkResponseCode(responseCode: Int) {
        if (responseCode == 401) {
            if (hostInfo.hasAuth()) {
               error("Bad credentials supplied for ${hostInfo.url}")
            } else {
                error("This repo requires authentication: ${hostInfo.url}")
            }
        }

    }

    /** The Kotlin compiler is about 17M and downloading it with the default buffer size takes forever */
    private val estimatedSize: Int
        get() = if (hostInfo.url.contains("kotlin-compiler")) 18000000 else 1000000

    fun toOutputStream(os: OutputStream, progress: (Long) -> Unit) = copy(inputStream, os, progress)

    fun toFile(file: File, progress: (Long) -> Unit = {}) = FileOutputStream(file).use {
        toOutputStream(it, progress)
    }

    private fun copy(from: InputStream, to: OutputStream, progress: (Long) -> Unit = {}) : Long {
        val estimate =
            if (connection is HttpURLConnection) {
                (connection as HttpURLConnection).let {
                    it.contentLength
                }
            } else {
                estimatedSize
            }

        val buf = ByteArray(estimatedSize)
        var total: Long = 0
        while (true) {
            val r = from.read(buf)
            if (r == -1) {
                break
            }
            to.write(buf, 0, r)
            total += r.toLong()
            progress(total * 100 / estimate)
        }
        return total
    }

    val string: String
        get() {
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = reader.readLine()
            while (line != null) {
                sb.append(line).append('\n')
                line = reader.readLine()
            }

            return sb.toString()
        }
}
