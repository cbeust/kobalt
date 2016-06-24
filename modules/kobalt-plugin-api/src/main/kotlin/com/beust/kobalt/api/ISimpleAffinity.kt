package com.beust.kobalt.api

/**
 * An affinity interface that gets run without a project nor a context.
 */
interface ISimpleAffinity<T> : IAffinity {
    /**
     * @return an integer indicating the affinity of your actor. The actor that returns
     * the highest affinity gets selected.
     */
    fun affinity(project: T) : Int
}