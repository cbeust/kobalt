package com.beust.kobalt.internal

import com.beust.kobalt.api.*

/**
 * Base class for JVM language plug-ins.
 */
abstract class BaseJvmPlugin<T>: ConfigPlugin<T>(), IProjectContributor, ICompilerFlagContributor {

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        project.projectProperties.put(JvmCompilerPlugin.DEPENDENT_PROJECTS, projects())
    }

    private val allProjects = arrayListOf<ProjectDescription>()

    fun addDependentProjects(project: Project, dependents: List<Project>) {
        project.projectInfo.dependsOn.addAll(dependents)
        with(ProjectDescription(project, dependents)) {
            allProjects.add(this)
        }
    }

    // IProjectContributor
    override fun projects() = allProjects
}
