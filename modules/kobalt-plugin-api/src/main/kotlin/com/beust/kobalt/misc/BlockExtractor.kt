package com.beust.kobalt.misc

import com.beust.kobalt.homeDir
import java.io.File
import java.util.regex.Pattern

fun main(argv: Array<String>) {
    val lines = File(homeDir("kotlin/kobalt/kobalt/src/Build.kt")).readLines()
    val result = BlockExtractor(Pattern.compile("val.*buildScript.*\\{"), '{', '}').extractBlock(lines)

//    BlockExtractor("plugins", '(', ')').extractBlock(lines)
}

/**
 * Used to extract a keyword followed by opening and closing tags out of a list of strings,
 * e.g. buildScript { ... }.
 */
class BlockExtractor(val regexp: Pattern, val opening: Char, val closing: Char) {
    fun extractBlock(lines: List<String>): String? {
        var foundKeyword = false
        var foundClosing = false
        var count = 0
        val result = StringBuffer()

        fun updateCount(line: String) {
            val currentLine = StringBuffer()
            line.toCharArray().forEach { c ->
                if (c == opening) {
                    count++
                }
                if (c == closing) {
                    count--
                    if (count == 0) {
                        currentLine.append(closing).append("\n")
                        foundClosing = true
                    }
                }
                if (foundKeyword && count > 0) currentLine.append(c)
            }

            if (currentLine.isNotEmpty()) result.append(currentLine.toString()).append("\n")
        }

        lines.forEach { line ->
            val found = regexp.matcher(line).matches()
            if (found) {
                foundKeyword = true
                count = 1
                result.append(line).append("\n")
            } else {
                updateCount(line)
            }

            if (foundKeyword && foundClosing && count == 0) {
                println("Done extracting: @$result@")
                return result.toString()
            }
        }

        if (foundKeyword && foundClosing && count == 0) {
            return result.toString()
        } else {
            return null
        }
    }
}
