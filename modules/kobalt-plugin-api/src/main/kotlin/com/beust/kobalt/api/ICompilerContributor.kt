package com.beust.kobalt.api

import com.beust.kobalt.TaskResult

interface ICompiler {
    /**
     * The suffixes handled by this compiler (without the dot, e.g. "java" or "kt").
     */
    val sourceSuffixes: List<String>

    /**
     * Run the compilation based on the info.
     */
    fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult
}

interface ICompilerContributor : IProjectAffinity {
    fun compilersFor(project: Project, context: KobaltContext): List<ICompiler>
}
