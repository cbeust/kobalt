package com.beust.kobalt.api

import com.beust.kobalt.IncrementalTaskInfo

/**
 * Plug-ins that will be invoked during the "assemble" task and wish to return an incremental task instead
 * of a regular one.
 */
interface IIncrementalAssemblyContributor {
    fun assemble(project: Project, context: KobaltContext) : IncrementalTaskInfo
}

