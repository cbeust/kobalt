package com.beust.kobalt.app

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.warn

class Profiles(val context: KobaltContext) {
    fun applyProfiles(lines: List<String>): BuildFiles.SplitBuildFile {
        val imports = arrayListOf<String>()
        val code = arrayListOf<String>()
        var containsProfiles = false
        lines.forEach { line ->
            val isImport = line.startsWith("import")
            val correctLine = correctProfileLine(line)
            containsProfiles = containsProfiles or correctLine.second
            if (isImport) imports.add(correctLine.first)
            else code.add(correctLine.first)
        }
        return BuildFiles.SplitBuildFile(imports, code, containsProfiles)
    }

    /**
     * If the current line matches one of the profiles, turn the declaration into
     * val profile = true, otherwise return the same line.
     *
     * @return the line adjusted with the profile and a boolean indicating if a profile was detected in that line.
     */
    fun correctProfileLine(line: String): Pair<String, Boolean> {
        var containsProfiles = false
        (context.profiles as List<String>).forEach { profile ->
            val re = Regex(".*va[rl][ \\t]+([a-zA-Z0-9_]+)[ \\t]*.*profile\\(\\).*")
            val oldRe = Regex(".*va[rl][ \\t]+([a-zA-Z0-9_]+)[ \\t]*=[ \\t]*[tf][ra][ul][es].*")
            val matcher = re.matchEntire(line)
            val oldMatcher = oldRe.matchEntire(line)

            fun profileMatch(matcher: MatchResult?) : Pair<Boolean, String?> {
                val variable = if (matcher != null) matcher.groups[1]?.value else null
                return Pair(profile == variable, variable)
            }

            if ((matcher != null && matcher.groups.isNotEmpty())
                    || (oldMatcher != null && oldMatcher.groups.isNotEmpty())) {
                containsProfiles = true
                val match = profileMatch(matcher)
                val oldMatch = profileMatch(oldMatcher)
                if (match.first || oldMatch.first) {
                    val variable = if (match.first) match.second else oldMatch.second

                    if (oldMatch.first) {
                        warn("Old profile syntax detected for \"$line\"," +
                                " please update to \"val $variable by profile()\"")
                    }

                    with("val $variable = true") {
                        kobaltLog(2, "  Activating profile $profile in build file")
                        return Pair(this, containsProfiles)
                    }
                }
            }
        }
        return Pair(line, containsProfiles)
    }
}
