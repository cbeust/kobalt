package com.beust.kobalt.api

/**
 * Modify a list of dependencies before Kobalt starts using them.
 */
interface IClasspathInterceptor : IInterceptor {
    fun intercept(project: Project, dependencies: List<IClasspathDependency>): List<IClasspathDependency>
}
