package com.beust.kobalt.api

import com.beust.kobalt.api.IClasspathDependency

/**
 * Modify a list of dependencies before Kobalt starts using them.
 */
interface IClasspathInterceptor : IInterceptor {
    fun intercept(dependencies: List<IClasspathDependency>) : List<IClasspathDependency>
}
