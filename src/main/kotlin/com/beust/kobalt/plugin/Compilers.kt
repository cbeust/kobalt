package com.beust.kobalt.plugin

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.CompilerActionInfo
import com.beust.kobalt.api.ICompilerDescription
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.warn

interface ICompiler {
    fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult
}

class CompilerDescription(override val sourceSuffixes: List<String>, override val sourceDirectory: String,
        val compiler: ICompiler, override val priority: Int = ICompilerDescription.DEFAULT_PRIORITY)
        : ICompilerDescription {
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

