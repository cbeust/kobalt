package com.beust.kobalt.api

import com.beust.kobalt.api.annotation.Directive
import java.util.*

/**
 * Various elements in a build file let you specify dependencies: projects, buildType and productFlavor.
 * They all implement this interface and delegate to an instance of the `DependencyHolder` concrete class.
 */
interface IDependencyHolder {
    var project: Project

    val compileDependencies : ArrayList<IClasspathDependency>
    val compileProvidedDependencies : ArrayList<IClasspathDependency>
    val compileRuntimeDependencies : ArrayList<IClasspathDependency>
    val excludedDependencies : ArrayList<IClasspathDependency>
    val nativeDependencies : ArrayList<IClasspathDependency>

    @Directive
    var dependencies: Dependencies?

    @Directive
    fun dependencies(init: Dependencies.() -> Unit) : Dependencies
}

open class DependencyHolder : IDependencyHolder {
    override lateinit var project: Project
    override val compileDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    override val compileProvidedDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    override val compileRuntimeDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    override val excludedDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    override val nativeDependencies : ArrayList<IClasspathDependency> = arrayListOf()

    override var dependencies : Dependencies? = null

    override fun dependencies(init: Dependencies.() -> Unit) : Dependencies {
        dependencies = Dependencies(project, compileDependencies, compileProvidedDependencies,
                compileRuntimeDependencies, excludedDependencies, nativeDependencies)
        dependencies!!.init()
        return dependencies!!
    }
}

