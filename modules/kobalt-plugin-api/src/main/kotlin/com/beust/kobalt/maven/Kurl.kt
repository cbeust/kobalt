package com.beust.kobalt.maven

import com.beust.kobalt.HostConfig
import com.beust.kobalt.KobaltException
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.LocalProperties
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.*

/**
 * Abstracts a URL so that it works transparently on either http:// or file://
 */
class Kurl(val hostInfo: HostConfig) {
    companion object {
        const val KEY = "authUrl"
        const val VALUE_USER = "username"
        const val VALUE_PASSWORD = "password"
    }

    init {
        // See if the URL needs to be authenticated. Look in local.properties for keys
        // of the format authUrl.<host>.user=xxx and authUrl.<host>.password=xxx
        val properties = LocalProperties().localProperties
        val host = java.net.URL(hostInfo.url).host
        properties.entries.forEach {
            val key = it.key.toString()
            if (key == "$KEY.$host.$VALUE_USER") {
                hostInfo.username = properties.getProperty(key)
            } else if (key == "$KEY.$host.$VALUE_PASSWORD") {
                hostInfo.password = properties.getProperty(key)
            }
        }
        fun error(s1: String, s2: String) {
            throw KobaltException("Found \"$s1\" but not \"$s2\" in local.properties for $KEY.$host",
                    docUrl = "http://beust.com/kobalt/documentation/index.html#maven-repos-authenticated")
        }
        if (! hostInfo.username.isNullOrBlank() && hostInfo.password.isNullOrBlank()) {
            error("username", "password")
        } else if(hostInfo.username.isNullOrBlank() && ! hostInfo.password.isNullOrBlank()) {
            error("password", "username")
        }
    }

    val connection : URLConnection
        get() {
            val result = URL(hostInfo.url).openConnection()
            if (hostInfo.hasAuth()) {
                val userPass = hostInfo.username + ":" + hostInfo.password
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
            val url = hostInfo.url
            val result =
                    if (connection is HttpURLConnection) {
                        val responseCode = (connection as HttpURLConnection).responseCode
                        checkResponseCode(responseCode)
                        responseCode == 200 || responseCode == 301
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
