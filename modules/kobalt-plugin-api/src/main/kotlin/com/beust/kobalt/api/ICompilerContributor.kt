package com.beust.kobalt.api

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.IClasspathDependency
import java.io.File

interface ICompilerContributor : IProjectAffinity {
    fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult
}
