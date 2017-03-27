package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.build.BuildSources
import com.beust.kobalt.misc.kobaltLog
import com.google.inject.Inject
import java.util.*

class ProjectFinder @Inject constructor(val buildFileCompilerFactory: BuildFileCompiler.IFactory,
        val pluginInfo: PluginInfo, val plugins: Plugins) {

    fun initForBuildFile(buildSources: BuildSources, args: Args): List<Project> {
        val findProjectResult = buildFileCompilerFactory.create(buildSources, pluginInfo)
                .compileBuildFiles(args)
        if (! findProjectResult.taskResult.success) {
            throw KobaltException("Couldn't compile build file: " + findProjectResult.taskResult.errorMessage)
        }

        val allProjects = findProjectResult.projects
        findProjectResult.context.allProjects.addAll(allProjects)

        //
        // Now that we have projects, add all the repos from repo contributors that need a Project
        //
        allProjects.forEach { project ->
            pluginInfo.repoContributors.forEach {
                it.reposFor(project).forEach {
                    Kobalt.addRepo(it)
                }
            }
        }

        //
        // Run all the dependencies through the IDependencyInterceptors
        //
        runClasspathInterceptors(allProjects)

        kobaltLog(2, "Final list of repos:\n  " + Kobalt.repos.joinToString("\n  "))

        //
        // Call apply() on all plug-ins now that the repos are set up
        //
        plugins.applyPlugins(Kobalt.context!!, allProjects)

        return allProjects
    }

    private fun runClasspathInterceptors(allProjects: List<Project>) {
        allProjects.forEach {
            runClasspathInterceptors(it, it.compileDependencies)
            runClasspathInterceptors(it, it.compileProvidedDependencies)
            runClasspathInterceptors(it, it.compileRuntimeDependencies)
            runClasspathInterceptors(it, it.testProvidedDependencies)
            runClasspathInterceptors(it, it.testDependencies)
            runClasspathInterceptors(it, it.nativeDependencies)
        }
    }

    private fun runClasspathInterceptors(project: Project, dependencies: ArrayList<IClasspathDependency>)
            = with(dependencies) {
        if (pluginInfo.classpathInterceptors.size > 0) {
            val deps = interceptDependencies(project, pluginInfo, this)
            clear()
            addAll(deps)
        } else {
            this
        }
    }

    private fun interceptDependencies(project: Project, pluginInfo: PluginInfo,
            dependencies: ArrayList<IClasspathDependency>) : ArrayList<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        pluginInfo.classpathInterceptors.forEach {
            result.addAll(it.intercept(project, dependencies))
        }
        return result
    }

}

