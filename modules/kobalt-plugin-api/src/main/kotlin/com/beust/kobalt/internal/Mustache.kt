package com.beust.kobalt.internal

import com.github.mustachejava.DefaultMustacheFactory
import java.io.*

class Mustache {
    companion object {
        fun generateFile(mustacheIns: InputStream, createdFile: File, map: Map<String, Any>) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            var mf = DefaultMustacheFactory()
            mf.compile(InputStreamReader(mustacheIns), "kobalt").execute(pw, map).flush()
            with(createdFile) {
                parentFile.mkdirs()
                writeText(sw.toString())
            }
        }
    }
}
