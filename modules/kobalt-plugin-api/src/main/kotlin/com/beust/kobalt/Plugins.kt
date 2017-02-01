package com.beust.kobalt

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.IncrementalManager
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.JarUtils
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.kobaltLog
import com.google.inject.Provider
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.util.*
import java.util.jar.JarFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Plugins @Inject constructor (val taskManagerProvider : Provider<TaskManager>,
        val files: KFiles,
        val depManager: DependencyManager,
        val settings: KobaltSettings,
        val executors: KobaltExecutors,
        val pluginInfo: PluginInfo,
        val incrementalManagerFactory: IncrementalManager.IFactory,
        val taskManager: TaskManager) {

    companion object {
        private var pluginMap = hashMapOf<String, IPlugin>()

        fun addPluginInstance(plugin: IPlugin) {
            pluginMap.put(plugin.name, plugin)
        }

        val plugins : List<IPlugin>
            get() = ArrayList(pluginMap.values)

        /**
         * The list of plugins found in the build file.
         */
        val dynamicPlugins : ArrayList<IClasspathDependency> = arrayListOf()
        fun addDynamicPlugin(plugin: IClasspathDependency) = dynamicPlugins.add(plugin)

        fun findPlugin(name: String) : IPlugin? = pluginMap[name]
    }

    fun shutdownPlugins() = plugins.forEach { it.shutdown() }

    fun applyPlugins(context: KobaltContext, projects: List<Project>) {
        plugins.forEach { plugin ->
            addPluginInstance(plugin)
            // We could inject this in the plug-in but since these will be written by external users,
            // I want to keep the verbosity of plugins to a minimum, so instead, we do the injection
            // manually here
            if (plugin is BasePlugin) {
                plugin.taskManager = taskManagerProvider.get()
                plugin.plugins = this
            }

            // Call apply() on each plug-in that accepts a project
            kobaltLog(2, "Applying plug-in \"${plugin.name}\"")
            projects.filter { plugin.accept(it) }.forEach { project ->
                plugin.apply(project, context)
            }

            findStaticTasks(plugin, Task::class.java, { method -> isValidTaskMethod(method)}).forEach {
                taskManager.addAnnotationTask(plugin, it.first, it.second)
            }
            findStaticTasks(plugin, IncrementalTask::class.java,
                    { method -> isValidIncrementalTaskMethod(method)}).forEach {
            taskManager.addIncrementalTask(plugin, it.first, it.second)
            }
        }

        // Collect all the tasks from the task contributors
        context.pluginInfo.taskContributors.forEach {
            projects.forEach { project ->
                taskManager.dynamicTasks.addAll(it.tasksFor(project, context))
            }
        }

        // ... and from the incremental task contributors
        val incrementalManager = incrementalManagerFactory.create()
        context.pluginInfo.incrementalTaskContributors.forEach {
            projects.forEach { project ->
                it.incrementalTasksFor(project, context).forEach {
                    // Convert the closure (Project) -> IncrementalTaskInfo to (Project) -> TaskResult
                    // and create a DynamicTask out of it
                    val closure =
                            incrementalManager.toIncrementalTaskClosure(it.name, it.incrementalClosure, Variant())
                    val task = DynamicTask(it.plugin, it.name, it.doc, it.group, it.project, it.dependsOn,
                            it.reverseDependsOn, it.runBefore, it.runAfter, it.alwaysRunAfter,
                            closure)
                    taskManager.dynamicTasks.add(task)
                }
            }
        }

        // Now that we have collected all static and dynamic tasks, turn them all into plug-in tasks
        taskManager.computePluginTasks(projects)
    }

    private fun <T: Annotation> findStaticTasks(plugin: IPlugin, klass: Class<T>, validate: (Method) -> Boolean)
            : List<Pair<Method, T>> {
        val result = arrayListOf<Pair<Method, T>>()

        var currentClass : Class<in Any>? = plugin.javaClass

        // Tasks can come from two different places: plugin classes and build files.
        // When a task is read from a build file, ScriptCompiler adds it right away to plugin.methodTasks.
        // The following loop introspects the current plugin, finds all the tasks using the @Task annotation
        // and adds them to plugin.methodTasks
        while (currentClass != null && ! (klass.equals(currentClass))) {
            currentClass.declaredMethods.map {
                Pair(it, it.getAnnotation(klass))
            }.filter {
                it.second != null
            }.filter {
                validate(it.first)
            }.forEach {
                if (Modifier.isPrivate(it.first.modifiers)) {
                    throw KobaltException("A task method cannot be private: ${it.first}")
                }
                result.add(it)
            }

            currentClass = currentClass.superclass
        }
        return result
    }

    /**
     * Make sure this task method has the right signature.
     */
    private fun isValidIncrementalTaskMethod(method: Method): Boolean {
        val t = "Task ${method.declaringClass.simpleName}.${method.name}: "

        if (method.returnType != IncrementalTaskInfo::class.java) {
            throw IllegalArgumentException("${t}should return a IncrementalTaskInfo")
        }
        return true
    }

    /**
     * Make sure this task method has the right signature.
     */
    private fun isValidTaskMethod(method: Method): Boolean {
        val t = "Task ${method.declaringClass.simpleName}.${method.name}: "

        if (method.returnType != TaskResult::class.java) {
            throw IllegalArgumentException("${t}should return a TaskResult")
        }
        if (method.parameterTypes.size != 1) {
            throw IllegalArgumentException("${t}should take exactly one parameter of type a Project")
        }
        with(method.parameterTypes) {
            if (! Project::class.java.isAssignableFrom(get(0))) {
                throw IllegalArgumentException("${t}first parameter should be of type Project," +
                        "not ${get(0)}")
            }
        }
        return true
    }

    val dependencies = arrayListOf<IClasspathDependency>()

    fun installPlugins(dependencies: List<IClasspathDependency>, scriptClassLoader: ClassLoader) {
        val executor = executors.newExecutor("Plugins", 5)
        dependencies.forEach {
            //
            // Load all the jar files synchronously (can't compile the build script until
            // they are installed locally).
            depManager.create(it.id)

            //
            // Open the jar, parse its kobalt-plugin.xml and add the resulting PluginInfo to pluginInfo
            //
            val file = it.jarFile.get()
            val pluginXml = if (file.isDirectory) {
                // The plug-in can point to a directory (e.g. plugin("classes")), in which case we just
                // read kobalt-plugin.xml directly
                File(file, PluginInfo.PLUGIN_XML).readText()
            } else {
                // The plug-in is pointing to a jar file, read kobalt-plugin.xml from it
                JarUtils.extractTextFile(JarFile(file), PluginInfo.PLUGIN_XML)
            }
            if (pluginXml != null) {
                val pluginClassLoader = URLClassLoader(arrayOf(file.toURI().toURL()))
                val thisPluginInfo = PluginInfo.readPluginXml(pluginXml, pluginClassLoader, scriptClassLoader)
                pluginInfo.addPluginInfo(thisPluginInfo)
                thisPluginInfo.plugins.forEach {
                    Plugins.addPluginInstance(it)
                }
            } else {
                throw KobaltException("Plugin $it doesn't contain a ${PluginInfo.PLUGIN_XML} file")
            }
       }
        executor.shutdown()
    }

}