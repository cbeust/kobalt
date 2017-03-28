package com.beust.kobalt.misc

import com.beust.kobalt.homeDir
import java.io.File
import java.util.regex.Pattern

fun main(argv: Array<String>) {
    val lines = File(homeDir("kotlin/kobalt/kobalt/src/Build.kt")).readLines()
    val result = BlockExtractor(Pattern.compile("val.*buildScript.*\\{"), '{', '}').extractBlock(lines)
//    BlockExtractor("plugins", '(', ')').extractBlock(lines)
}

class Section(val start: Int, val end: Int) {
    override fun toString() = "$start-$end"
}

class BuildScriptInfo(val content: String, val sections: List<Section>) {
    fun isInSection(lineNumber: Int): Boolean {
        sections.forEach {
            if (lineNumber >= it.start && lineNumber <= it.end) return true
        }
        return false
    }
}

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
        val buildScript = arrayListOf<String>()
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

            if (currentLine.isNotEmpty() && foundKeyword) buildScript.add(currentLine.toString())
        }

        val allowedImports = listOf("com.beust", "java")
        val disallowedImports = listOf("com.beust.kobalt.plugin")
        val imports = arrayListOf<String>()
        val sections = arrayListOf<Section>()
        lines.forEach { line ->
            currentLineNumber++
            val found = regexp.matcher(line).matches()
            if (found) {
                startLine = currentLineNumber
                foundKeyword = true
                count = 1
                buildScript.add(line)
                topLines.add(line)
            } else {
                if (line.startsWith("import")) {
                    if (allowedImports.any { line.contains(it) } && !disallowedImports.any { line.contains(it) }) {
                        imports.add(line)
                    }
                } else {
                    topLines.add(line)
                }
                updateCount(line)
            }

            if (foundKeyword && foundClosing && count == 0) {
                sections.add(Section(startLine, endLine))
                foundKeyword = false
                foundClosing = false
                count = 0
                startLine = 0
                endLine = 0
            }
        }

        if (sections.isNotEmpty()) {
            val result = (imports.distinct() + buildScript).joinToString("\n") + "\n"

            return BuildScriptInfo(result, sections)
        } else {
            return null
        }
    }
}
