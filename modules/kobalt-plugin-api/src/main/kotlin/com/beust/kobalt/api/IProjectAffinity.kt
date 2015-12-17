package com.beust.kobalt.api

/**
 * An affinity interface that uses a project and a context to calculate its affinity.
 */
interface IProjectAffinity : IAffinity {
    /**
     * @return an integer indicating the affinity of your actor for the given project. The actor that returns
     * the highest affinity gets selected.
     */
    fun affinity(project: Project, context: KobaltContext) : Int
}
