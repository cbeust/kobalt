package com.beust.kobalt.api

import com.beust.kobalt.HostInfo

/**
 * Plugins that add their own repos.
 */
interface IRepoContributor : IContributor {
    /**
     * Note that the project passed might be null because this contributor is called twice:
     * before the build file gets parsed (so we don't have any projects yet) and after the
     * build file has been parsed (then it gets called once for each project discovered).
     */
    fun reposFor(project: Project?) : List<HostInfo>
}

