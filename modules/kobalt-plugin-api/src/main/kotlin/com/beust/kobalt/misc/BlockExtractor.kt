package com.beust.kobalt.misc

import java.io.File
import java.util.regex.Pattern

class Section(val start: Int, val end: Int) {
    override fun toString() = "$start-$end"
}

class IncludedBuildSourceDir(val line: Int, val dirs: List<String>)

class BuildScriptInfo(val file: File, val fullBuildFile: List<String>, val sections: List<Section>,
        val imports: List<String>) {
    fun isInSection(lineNumber: Int): Boolean {
        sections.forEach {
            if (lineNumber >= it.start && lineNumber <= it.end) return true
        }
        return false
    }

    val includedBuildSourceDirs = arrayListOf<IncludedBuildSourceDir>()

    fun includedBuildSourceDirsForLine(line: Int): List<String> {
        val result = includedBuildSourceDirs.find { it.line == line }?.dirs
        return result ?: emptyList()
    }
}

/**
 * Used to extract a keyword followed by opening and closing tags out of a list of strings,
 * e.g. buildScript { ... }.
 */
class BlockExtractor(val regexp: Pattern, val opening: Char, val closing: Char) {
    fun extractBlock(file: File, lines: List<String>): BuildScriptInfo? {
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

        val imports = arrayListOf<String>()
        val sections = arrayListOf<Section>()
        lines.forEach { line ->
            val found = regexp.matcher(line).matches()
            if (found) {
                startLine = currentLineNumber
                foundKeyword = true
                count = 1
                buildScript.add(line)
                topLines.add(line)
            } else {
                if (line.startsWith("import")) {
                    if (isAllowedImport(line)) {
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

            currentLineNumber++
        }

        if (sections.isNotEmpty()) {
            val result = (imports.distinct() + buildScript).joinToString("\n") + "\n"

            return BuildScriptInfo(file, lines, sections, imports)
        } else {
            return null
        }
    }

    companion object {
        private val allowedImports = listOf("com.beust", "java")
        private val disallowedImports = listOf("com.beust.kobalt.plugin")

        fun isAllowedImport(line: String) : Boolean {
            return allowedImports.any { line.contains(it) } && !disallowedImports.any { line.contains(it) }
        }
    }
}
