package com.beust.kobalt.api

/**
 * Plug-ins that want to override the local maven repo path.
 */
interface ILocalMavenRepoPathInterceptor : IInterceptor {
    fun repoPath(currentPath: String) : String
}
