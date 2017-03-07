package com.beust.kobalt.misc

import com.beust.kobalt.homeDir
import java.io.File
import java.util.regex.Pattern

fun main(argv: Array<String>) {
    val lines = File(homeDir("kotlin/kobalt/kobalt/src/Build.kt")).readLines()
    val result = BlockExtractor(Pattern.compile("val.*buildScript.*\\{"), '{', '}').extractBlock(lines)

//    BlockExtractor("plugins", '(', ')').extractBlock(lines)
}

class BuildScriptInfo(val content: String, val startLine: Int, val endLine: Int)

/**
 * Used to extract a keyword followed by opening and closing tags out of a list of strings,
 * e.g. buildScript { ... }.
 */
class BlockExtractor(val regexp: Pattern, val opening: Char, val closing: Char) {
    fun extractBlock(lines: List<String>): BuildScriptInfo? {
        var currentLineNumber = 0
        // First line of the buildScript block
        var startLine = 0
        // Last line of the buildScript block
        var endLine = 0
        var foundKeyword = false
        var foundClosing = false
        var count = 0
        val result = StringBuffer()
        val topLines = arrayListOf<String>()

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
                        endLine = currentLineNumber
                    }
                }
                if (foundKeyword && count > 0) currentLine.append(c)
            }

            if (currentLine.isNotEmpty()) result.append(currentLine.toString()).append("\n")
        }

        lines.forEach { line ->
            currentLineNumber++
            val found = regexp.matcher(line).matches()
            if (found) {
                startLine = currentLineNumber
                foundKeyword = true
                count = 1
                result.append(topLines.joinToString("\n"))
                result.append(line).append("\n")
            } else {
                topLines.add(line)
                updateCount(line)
            }

            if (foundKeyword && foundClosing && count == 0) {
                return BuildScriptInfo(result.toString(), startLine, endLine)
            }
        }

        if (foundKeyword && foundClosing && count == 0) {
            return BuildScriptInfo(result.toString(), startLine, endLine)
        } else {
            return null
        }
    }
}
