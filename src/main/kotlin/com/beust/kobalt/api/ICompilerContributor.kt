package com.beust.kobalt.api

import com.beust.kobalt.TaskResult
import com.beust.kobalt.maven.dependency.IClasspathDependency
import java.io.File

interface ICompilerContributor : IAffinity {
    fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult
}

data class CompilerActionInfo(val directory: String?,
        val dependencies: List<IClasspathDependency>,
        val sourceFiles: List<String>,
        val outputDir: File,
        val compilerArgs: List<String>)

