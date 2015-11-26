package com.beust.kobalt.kotlin.internal

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.misc.countChar
import java.io.File
import java.nio.charset.Charset
import java.util.*

class ParsedBuildFile(val file: File, val context: KobaltContext) {
    val plugins = arrayListOf<String>()
    val repos = arrayListOf<String>()
    val profileLines = arrayListOf<String>()

    private val preBuildScript = arrayListOf("import com.beust.kobalt.*",
            "import com.beust.kobalt.api.*")
    val preBuildScriptCode : String get() = preBuildScript.joinToString("\n")

    private val buildScript = arrayListOf<String>()
    val buildScriptCode : String get() = buildScript.joinToString("\n")

    init {
        parseBuildFile()
    }

    private fun parseBuildFile() {
        var parenCount = 0
        file.forEachLine(Charset.defaultCharset()) { line ->
            var current: ArrayList<String>? = null
            var index = line.indexOf("plugins(")
            if (index >= 0) {
                current = plugins
            } else {
                index = line.indexOf("repos(")
                if (index >= 0) {
                    current = repos
                }
            }
            if (parenCount > 0 || current != null) {
                if (index == -1) index = 0
                with(line.substring(index)) {
                    parenCount += line countChar '('
                    if (parenCount > 0) {
                        current!!.add(line)
                    }
                    parenCount -= line countChar ')'
                }
            }

            /**
             * If the current line matches one of the profile, turns the declaration into
             * val profile = true, otherwise return the same line
             */
            fun correctProfileLine(line: String) : String {
                if (line.contains("experimental")) {
                    println("DONOTCOMMIT")
                }
                context.profiles.forEach {
                    if (line.matches(kotlin.text.Regex("[ \\t]*val[ \\t]+$it[ \\t]+=.*"))) {
                        with("val $it = true") {
                            profileLines.add(this)
                            return this
                        }
                    }
                }
                return line
            }

            buildScript.add(correctProfileLine(line))
        }

        repos.forEach { preBuildScript.add(it) }
        plugins.forEach { preBuildScript.add(it) }
    }
}

