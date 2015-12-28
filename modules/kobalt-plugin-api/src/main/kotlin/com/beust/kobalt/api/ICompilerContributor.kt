package com.beust.kobalt.api

import com.beust.kobalt.TaskResult

interface ICompilerContributor : IProjectAffinity {
    fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult
}
