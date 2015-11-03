package com.beust.kobalt.api

import com.beust.kobalt.plugin.java.JavaPlugin
import com.beust.kobalt.plugin.kotlin.KotlinPlugin

class ProjectDescription(val project: Project, val dependsOn: List<Project>)

interface IProjectContributor {
    fun projects() : List<ProjectDescription>
}

class KobaltPluginFile {
    fun <T> instanceOf(c: Class<T>) = Kobalt.INJECTOR.getInstance(c)
    val projectContributors : List<Class<out IProjectContributor>> =
            arrayListOf(JavaPlugin::class.java, KotlinPlugin::class.java)

    // Future: compilerArgs contributor
}