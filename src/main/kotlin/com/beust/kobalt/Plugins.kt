package com.beust.kobalt

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.kotlin.BuildFile
import com.beust.kobalt.kotlin.ScriptCompiler
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.KobaltException
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.countChar
import com.beust.kobalt.plugin.DefaultPlugin
import com.beust.kobalt.plugin.java.JavaPlugin
import com.beust.kobalt.plugin.kotlin.KotlinPlugin
import com.beust.kobalt.plugin.packaging.PackagingPlugin
import com.beust.kobalt.plugin.publish.PublishPlugin
import com.google.inject.Provider
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.ArrayList
import java.util.HashMap
import java.util.jar.JarInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class Plugins @Inject constructor (val taskManagerProvider : Provider<TaskManager>,
        val files: KFiles,
        val depFactory: DepFactory,
        val localRepo: LocalRepo,
        val executors: KobaltExecutors,
        val scriptCompilerFactory: ScriptCompiler.IFactory): KobaltLogger {

    companion object {
        public val MANIFEST_PLUGIN_CLASS : String = "Kobalt-Plugin-Class"

        private var pluginMap = hashMapOf<String, Plugin>()
        private var storageMap = HashMap<String, HashMap<String, Any>>()

        fun storeValue(pluginName: String, key: String, value: Any) {
            var values = storageMap.get(pluginName)
            if (values == null) {
                values = hashMapOf<String, Any>()
                storageMap.put(pluginName, values)
            }
            values.put(key, value)
        }

        fun getValue(pluginName: String, key: String) : Any? {
            return storageMap.get(pluginName)?.get(key)
        }

        val defaultPlugin : Plugin get() = getPlugin(DefaultPlugin.NAME)!!

        fun addPlugin(pluginClass : Class<out Plugin>) {
            addPluginInstance(Kobalt.INJECTOR.getInstance(pluginClass))
        }

        private fun addPluginInstance(plugin: Plugin) {
            pluginMap.put(plugin.name, plugin)
        }

        init {
            arrayListOf<Class<out Plugin>>(
                    DefaultPlugin::class.java,
                    JavaPlugin::class.java,
                    KotlinPlugin::class.java,
                    PackagingPlugin::class.java,
                    PublishPlugin::class.java
//                    AptPlugin::class.java
            ).map {
                addPluginInstance(Kobalt.INJECTOR.getInstance(it))
            }
        }

        public fun getPlugin(name: String) : Plugin? = pluginMap.get(name)

        public val plugins : List<Plugin>
            get() = ArrayList(pluginMap.values())

        /**
         * The list of plugins found in the build file.
         */
        public val dynamicPlugins : ArrayList<IClasspathDependency> = arrayListOf()
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


            var currentClass : Class<in Any> = plugin.javaClass

            // Tasks can come from two different places: plugin classes and build files.
            // When a task is read from a build file, ScriptCompiler adds it right away to plugin.methodTasks.
            // The following loop introspects the current plugin, finds all the tasks using the @Task annotation
            // and adds them to plugin.methodTasks
            while (! (currentClass.equals(Any::class.java))) {
                currentClass.declaredMethods.map {
                    Pair(it, it.getAnnotation(Task::class.java))
                }.filter {
                    it.second != null
                }.filter {
                    isValidTaskMethod(it.first)
                }.forEach {
                    if (Modifier.isPrivate(it.first.modifiers)) {
                        throw KobaltException("A task method cannot be private: ${it.first}")
                    }
                    val annotation = it.second

                    log(3, "Adding MethodTask from @Task: ${it.first} $annotation")
                    plugin.methodTasks.add(Plugin.MethodTask(it.first, annotation))
                }

                currentClass = currentClass.superclass
            }

            // Now plugin.methodTasks contains both tasks from the build file and the plug-ins, we
            // can create the whole set of tasks and set up their dependencies
            plugin.methodTasks.forEach { methodTask ->
                val method = methodTask.method
                val annotation = methodTask.taskAnnotation

                fun toTask(m: Method, project: Project, plugin: Plugin): (Project) -> TaskResult {
                    val result: (Project) -> TaskResult = {
                        m.invoke(plugin, project) as TaskResult
                    }
                    return result
                }

                projects.filter { plugin.accept(it) }.forEach { project ->
                    plugin.addStaticTask(annotation, project, toTask(method, project, plugin))
                }
            }
        }
    }

    /**
     * Make sure this task method has the right signature.
     */
    private fun isValidTaskMethod(method: Method): Boolean {
        val t = "Task ${method.declaringClass.simpleName}.${method.name}: "

        if (method.returnType != TaskResult::class.java) {
            throw IllegalArgumentException("${t}should return a TaskResult")
        }
        if (method.parameterTypes.size() != 1) {
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

    /**
     * Jar files for all the plugins.
     */
    public val pluginJarFiles : ArrayList<String> = arrayListOf()

    val dependencies = arrayListOf<IClasspathDependency>()

    /**
     * Parse the build files, locate all the plugins, download them and make them available to be
     * used on the classpath of the build file.
     */
    fun installDynamicPlugins(files: List<BuildFile>) {
        //
        // Extract all the plugin() and repos() code into a separate script (pluginCode)
        //
        files.forEach {
            val pluginCode = arrayListOf<String>()
            var parenCount = 0
            it.path.toFile().forEachLine(Charset.defaultCharset()) { line ->
                if (line.startsWith("import")) {
                    pluginCode.add(line)
                }
                var index = line.indexOf("plugins(")
                if (index == -1) index = line.indexOf("repos(")
                if (parenCount > 0 || index >= 0) {
                    if (index == -1) index = 0
                    with(line.substring(index)) {
                        parenCount += line countChar '('
                        if (parenCount > 0) {
                            pluginCode.add(line)
                        }
                        parenCount -= line countChar ')'
                    }
                }
            }

            //
            // Compile and run pluginCode, which contains all the plugins() calls extracted. This
            // will add all the dynamic plugins found in this code to Plugins.dynamicPlugins
            //
            val pluginSourceFile = KFiles.createTempFile(".kt")
            pluginSourceFile.writeText(pluginCode.join("\n"), Charset.defaultCharset())
            log(2, "Saved ${pluginSourceFile.absolutePath}")
            scriptCompilerFactory.create(pluginJarFiles,
                    { n: String, j: File? -> instantiateClassName(n, j)
                }).compile(BuildFile(Paths.get(pluginSourceFile.absolutePath), "Plugins"),
                    it.lastModified(),
                    KFiles.findBuildScriptLocation(it, "preBuildScript.jar"))
        }

        //
        // Locate all the jar files for the dynamic plugins we just discovered
        //
        dependencies.addAll(dynamicPlugins.map {
            pluginJarFiles.add(it.jarFile.get().absolutePath)
            it
        })

        //
        // Materialize all the jar files, instantiate their plugin main class and add it to Plugins
        //
        val executor = executors.newExecutor("Plugins", 5)
        dependencies.forEach {
            //
            // Load all the jar files synchronously (can't compile the build script until
            // they are installed locally).
            depFactory.create(it.id, executor)

            //
            // Inspect the jar, open the manifest, instantiate the main class and add it to the plugin repo
            //
            var fis: FileInputStream? = null
            var jis: JarInputStream? = null
            try {
                fis = FileInputStream(it.jarFile.get())
                jis = JarInputStream(fis)
                val manifest = jis.getManifest()
                val mainClass = manifest.getMainAttributes().getValue(Plugins.MANIFEST_PLUGIN_CLASS) ?:
                        throw KobaltException("Couldn't find \"${Plugins.MANIFEST_PLUGIN_CLASS}\" in the " +
                                "manifest of ${it}")

                val pluginClassName = mainClass.removeSuffix(" ")
                val c = instantiateClassName(pluginClassName)
                @Suppress("UNCHECKED_CAST")
                Plugins.addPlugin(c as Class<BasePlugin>)
                log(1, "Added plugin ${c}")
            } finally {
                jis?.close()
                fis?.close()
            }
        }
        executor.shutdown()
    }

    public fun instantiateClassName(className : String, buildScriptJarFile: File? = null) : Class<*> {
//        fun jarToUrl(jarAbsolutePath: String) = URL("file://" + jarAbsolutePath)

        fun jarToUrl(path: String) = URL("jar", "", "file:${path}!/")

        // We need the jar files to be first in the url list otherwise the Build.kt files resolved
        // might be Kobalt's own
        val urls = arrayListOf<URL>()
        buildScriptJarFile?.let {
            urls.add(jarToUrl(it.absolutePath))
        }
        urls.add(jarToUrl(files.kobaltJar))
        urls.addAll(pluginJarFiles.map { jarToUrl(it) })
        val classLoader = URLClassLoader(urls.toArray(arrayOfNulls<URL>(urls.size())))

        try {
            log(2, "Instantiating ${className}")
            return classLoader.loadClass(className)
        } catch(ex: Exception) {
            throw KobaltException("Couldn't instantiate ${className}: ${ex}")
        }
    }

    val allTasks : List<PluginTask>
        get() {
            val result = arrayListOf<PluginTask>()
            Plugins.plugins.forEach { plugin ->
                result.addAll(plugin.tasks)
            }
            return result
    }

    /**
     * @return the tasks accepted by at least one project
     */
    fun findTasks(task: String): List<PluginTask> {
        val tasks = allTasks.filter { task == it.name }
        if (tasks.isEmpty()) {
            throw KobaltException("Couldn't find task ${task}")
        } else {
            return tasks
        }
    }

}