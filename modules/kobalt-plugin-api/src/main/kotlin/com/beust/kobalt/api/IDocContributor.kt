package com.beust.kobalt.api

import com.beust.kobalt.TaskResult

interface IDocContributor : IProjectAffinity {
    fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult
}

