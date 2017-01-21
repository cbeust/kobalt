package com.beust.kobalt.misc

import com.beust.kobalt.homeDir
import java.io.File

fun main(argv: Array<String>) {
    val lines = File(homeDir("kotlin/kobalt/kobalt/src/Build.kt")).readLines()
    val result = BlockExtractor("buildScript", '{', '}').extractBlock(lines)

    BlockExtractor("plugins", '(', ')').extractBlock(lines)
}

class BlockExtractor(val keyword: String, val opening: Char, val closing: Char) {

    fun extractBlock(lines: List<String>): String? {
        var foundKeyword = false
        var foundOpening = false
        var foundClosing = false
        var count = 0
        val result = StringBuffer()

        fun updateCount(line: String) {
            val onlyBlanks = foundKeyword && ! foundOpening
            var blanks = true
            line.toCharArray().forEach { c ->
                if (c == opening) {
                    count++
                    if (onlyBlanks && blanks) foundOpening = true
                }
                if (c == closing) {
                    count--
                    foundClosing = true
                }
                if (c != ' ' && c != '\t' && c != '\n') blanks = false
                result.append(c)
            }
            result.append("\n")
        }

        lines.forEach { line ->
            if (! foundKeyword) {
                val index = line.indexOf(keyword)
                if (index >= 0) {
                    foundKeyword = true
                    result.append(keyword)
                    updateCount(line.substring(index + keyword.length))
                }
            } else {
                updateCount(line)
            }

            if (foundKeyword && foundOpening && foundClosing && count == 0) {
                println("Done extracting: @$result@")
                return result.toString()
            }
        }

        return null
    }
}
