package com.beust.kobalt.api

/**
 * Plugins that create projects need to implement this interface.
 */
interface IProjectContributor : IContributor {
    fun projects(): List<ProjectDescription>
}

class ProjectDescription(val project: Project, val dependsOn: List<Project>)

