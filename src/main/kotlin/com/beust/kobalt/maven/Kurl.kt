package com.beust.kobalt.maven

import com.google.inject.assistedinject.*
import java.io.*
import java.net.*
import javax.inject.*

/**
 * Abstracts a URL so that it works transparently on either http:// or file://
 */
class Kurl @Inject constructor(@Assisted val url: String) {
    val connection : URLConnection by lazy { // TODO bug here: it is impossible to close connection
        URL(url).openConnection()
    }

    interface IFactory {
        fun create(url: String) : Kurl
    }

    val exists : Boolean
        get() {
            val result =
                    if (connection is HttpURLConnection) {
                        (connection as HttpURLConnection).responseCode == 200
                    } else if (url.startsWith(IClasspathDependency.PREFIX_FILE)) {
                        val fileName = url.substring(IClasspathDependency.PREFIX_FILE.length)
                        File(fileName).exists()
                    } else {
                        false
                    }
            return result
        }

    /** The Kotlin compiler is about 17M and downloading it with the default buffer size takes forever */
    private val estimatedSize: Int
        get() = if (url.contains("kotlin-compiler")) 18000000 else 1000000

    fun toOutputStream(os: OutputStream, progress: (Long) -> Unit) = copy(connection.inputStream, os, progress)

    fun toFile(file: File, progress: (Long) -> Unit = {}) = toOutputStream(FileOutputStream(file), progress)

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
            connection.inputStream.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))

                var line: String? = reader.readLine()
                while (line != null) {
                    sb.append(line).append('\n')
                    line = reader.readLine()
                }
            }

            return sb.toString()
        }
}
