package com.beust.kobalt.app

import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.IPlugin
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.Topological
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.KobaltPlugin
import com.google.inject.Inject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarInputStream

class BuildScriptUtil @Inject constructor(val plugins: Plugins, val files: KFiles,
        val taskManager: TaskManager) {
    val projects = arrayListOf<Project>()

    val defaultPlugin : IPlugin get() = Plugins.findPlugin(KobaltPlugin.PLUGIN_NAME)!!

    /**
     * Run the given preBuildScript (or buildScript) jar file, using a classloader made of the passed URL's.
     * This list is empty when we run preBuildScript.jar but for buildScript.jar, it contains the list of
     * URL's found from running preBuildScript.jar.
     */
    fun runBuildScriptJarFile(buildScriptJarFile: File, urls: List<URL>,
            context: KobaltContext) : List<Project> {
        var stream : InputStream? = null
        // The jar files used to load the plug-ins are:
        // - all the plug-ins found in the build file
        // - kobalt's own jar file
        val allUrls = (urls + arrayOf(buildScriptJarFile.toURI().toURL())
                + files.kobaltJar.map {File(it).toURI().toURL() }
                + Kobalt.buildFileClasspath.map { it.jarFile.get().toURI().toURL()})
            .toTypedArray()
        val classLoader = URLClassLoader(allUrls)

        //
        // Install all the plugins
        //
        plugins.installPlugins(Plugins.dynamicPlugins, classLoader)

        //
        // Classload all the jar files and invoke their methods
        //
        try {
            stream = JarInputStream(FileInputStream(buildScriptJarFile))
            var entry = stream.nextJarEntry

            val classes = hashSetOf<Class<*>>()
            while (entry != null) {
                val name = entry.name;
                if (name.endsWith(".class")) {
                    val className = name.substring(0, name.length - 6).replace("/", ".")
                    var cl : Class<*>? = classLoader.loadClass(className)
                    if (cl != null) {
                        classes.add(cl)
                    } else {
                        throw KobaltException("Couldn't instantiate $className")
                    }
                }
                entry = stream.nextJarEntry;
            }

            // Invoke all the "val" found on the _DefaultPackage class (the Build.kt file)
            classes.filter { cls ->
                cls.name != "_DefaultPackage"
            }.forEach { cls ->
                cls.methods.forEach { method ->
                    // Invoke vals and see if they return a Project
                    if (method.name.startsWith("get") && Modifier.isStatic(method.modifiers)) {
                        try {
                            val r = method.invoke(null)
                            if (r is Project) {
                                log(2, "Found project ${r.name} in class $cls")
                                projects.add(r)
                            }
                        } catch(ex: Throwable) {
                            throw ex.cause ?: KobaltException(ex)
                        }
                    } else {
                        method.getAnnotation(Task::class.java)?.let {
                            taskManager.addAnnotationTask(defaultPlugin, method, it)
                        }
                        method.getAnnotation(IncrementalTask::class.java)?.let {
                            taskManager.addIncrementalTask(defaultPlugin, method, it)
                        }

                    }}
            }
        } finally {
            stream?.close()
        }

        validateProjects(projects)

        //
        // Now that the build file has run, fetch all the project contributors, grab the projects from them and sort
        // them topologically
        //
        Topological<Project>().let { topologicalProjects ->
            val all = hashSetOf<Project>()
            context.pluginInfo.projectContributors.forEach { contributor ->
                val descriptions = contributor.projects()
                descriptions.forEach { pd ->
                    topologicalProjects.addNode(pd.project)
                    pd.dependsOn.forEach { dependsOn ->
                        topologicalProjects.addEdge(pd.project, dependsOn)
                        all.add(dependsOn)
                    }
                }
            }
            val result = topologicalProjects.sort()

            return result
        }
    }

    fun isUpToDate(buildFile: BuildFile, jarFile: File) =
            buildFile.exists() && jarFile.exists()
                    && buildFile.lastModified < jarFile.lastModified()

    /**
     * Make sure all the projects have a unique name.
     */
    private fun validateProjects(projects: List<Project>) {
        val seen = hashSetOf<String>()
        projects.forEach {
            if (seen.contains(it.name)) {
                throw KobaltException("Duplicate project name: $it")
            } else {
                seen.add(it.name)
            }
        }
    }
}
