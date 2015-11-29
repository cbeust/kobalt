package com.beust.kobalt.api

import com.beust.kobalt.TaskResult

interface IDocContributor : IAffinity {
    fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult
}

