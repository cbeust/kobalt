package com.beust.kobalt.api

/**
 * Plug-ins that want to
 */
interface ILocalMavenRepoPathInterceptor : IInterceptor {
    fun repoPath(currentPath: String) : String
}
