package com.beust.kobalt

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.JarUtils
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.google.inject.Provider
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.jar.JarFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class Plugins @Inject constructor (val taskManagerProvider : Provider<TaskManager>,
        val files: KFiles,
        val depFactory: DepFactory,
        val localRepo: LocalRepo,
        val executors: KobaltExecutors,
        val pluginInfo: PluginInfo,
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
            log(2, "Applying plug-in \"${plugin.name}\"")
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
            taskManager.dynamicTasks.addAll(it.tasksFor(context).map { TaskManager.PluginDynamicTask(it.plugin, it) })
        }

        // Now that we have collected all static and dynamic tasks, turn them all into plug-in tasks
        taskManager.computePluginTasks(projects)
    }

    private fun <T: Annotation> findStaticTasks(plugin: IPlugin, klass: Class<T>, validate: (Method) -> Boolean)
            : List<Pair<Method, T>> {
        val result = arrayListOf<Pair<Method, T>>()

        var currentClass : Class<in Any> = plugin.javaClass

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

    fun installPlugins(dependencies: List<IClasspathDependency>, classLoader: ClassLoader) {
        val executor = executors.newExecutor("Plugins", 5)
        dependencies.forEach {
            //
            // Load all the jar files synchronously (can't compile the build script until
            // they are installed locally).
            depFactory.create(it.id, executor)

            //
            // Open the jar, parse its kobalt-plugin.xml and add the resulting PluginInfo to pluginInfo
            //
            val pluginXml = JarUtils.extractTextFile(JarFile(it.jarFile.get()), PluginInfo.PLUGIN_XML)
            if (pluginXml != null) {
                val thisPluginInfo = PluginInfo.readPluginXml(pluginXml, classLoader)
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