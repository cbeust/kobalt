package com.beust.kobalt.maven

import com.google.inject.assistedinject.Assisted
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import javax.inject.Inject

/**
 * Abstracts a URL so that it works transparently on either http:// or file://
 */
public class Kurl @Inject constructor(@Assisted val url: String, val http: Http) {
    val connection : URLConnection by lazy {
        URL(url).openConnection()
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
            connection.inputStream.let { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))

                var line: String? = reader.readLine()
                try {
                    while (line != null) {
                        sb.append(line).append('\n')
                        line = reader.readLine()
                    }
                } finally {
                    inputStream.close()
                }
            }

            return sb.toString()
        }
}
