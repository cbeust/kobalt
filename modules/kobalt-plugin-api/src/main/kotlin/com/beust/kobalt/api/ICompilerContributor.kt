package com.beust.kobalt.api

import com.beust.kobalt.TaskResult
import com.beust.kobalt.misc.warn

interface ICompilerDescription : Comparable<ICompilerDescription> {
    /**
     * The name of the language compiled by this compiler.
     */
    val name: String

    /**
     * The suffixes handled by this compiler (without the dot, e.g. "java" or "kt").
     */
    val sourceSuffixes: List<String>

    /**
     * The trailing end of the source directory (e.g. "kotlin" in "src/main/kotlin")
     */
    val sourceDirectory: String

    /**
     * Run the compilation based on the info.
     */
    fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult

    companion object {
        val DEFAULT_PRIORITY: Int = 10
    }

    /**
     * The priority of this compiler. Lower priority compilers are run first.
     */
    val priority: Int get() = DEFAULT_PRIORITY

    override fun compareTo(other: ICompilerDescription) = priority.compareTo(other.priority)

    /**
     * Can this compiler be passed directories or does it need individual source files?
     */
    val canCompileDirectories: Boolean get() = false
}

interface ICompilerContributor : IProjectAffinity, IContributor {
    fun compilersFor(project: Project, context: KobaltContext): List<ICompilerDescription>
}

interface ICompiler {
    fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult
}

class CompilerDescription(override val name: String,  override val sourceDirectory: String,
        override val sourceSuffixes: List<String>, val compiler: ICompiler,
        override val priority: Int = ICompilerDescription.DEFAULT_PRIORITY) : ICompilerDescription {
    override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult {
        val result =
            if (info.sourceFiles.size > 0) {
                compiler.compile(project, context, info)
            } else {
                warn("Couldn't find any source files to compile")
                TaskResult()
            }
        return result
    }
}

