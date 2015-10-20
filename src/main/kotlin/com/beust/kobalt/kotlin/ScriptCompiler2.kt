package com.beust.kobalt.kotlin

import com.beust.kobalt.Args
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Plugin
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.KobaltServer
import com.beust.kobalt.maven.KobaltException
import com.beust.kobalt.misc.*
import com.beust.kobalt.plugin.kotlin.kotlinCompilePrivate
import com.google.inject.assistedinject.Assisted
import rx.subjects.PublishSubject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.jar.JarInputStream
import javax.inject.Inject

public class ScriptCompiler2 @Inject constructor(@Assisted("buildFiles") val buildFiles: List<BuildFile>,
        val files: KFiles, val plugins: Plugins) {

    interface IFactory {
        fun create(@Assisted("buildFiles") buildFiles: List<BuildFile>) : ScriptCompiler2
    }

    val observable = PublishSubject.create<BuildScriptInfo>()

    private val SCRIPT_JAR = "buildScript.jar"

    fun compileBuildFiles(args: Args): List<Project> {
        val context = KobaltContext(args)
        Kobalt.context = context

        val allProjects = findProjects()

        //
        // Force each project.directory to be an absolute path, if it's not already
        //
        allProjects.forEach {
            val fd = File(it.directory)
            if (! fd.isAbsolute) {
                it.directory =
                        if (args.buildFile != null) {
                            KFiles.findDotDir(File(args.buildFile)).parentFile.absolutePath
                        } else {
                            fd.absolutePath
                        }
            }
        }

        plugins.applyPlugins(context, allProjects)
        return allProjects
    }

    private fun findProjects(): List<Project> {
        val result = arrayListOf<Project>()
        buildFiles.forEach { buildFile ->
            val pluginUrls = findPlugInUrls(buildFile)
            val buildScriptJarFile = File(KFiles.findBuildScriptLocation(buildFile, SCRIPT_JAR))

            maybeCompileBuildFile(buildFile, buildScriptJarFile, pluginUrls)
            val buildScriptInfo = parseBuildScriptJarFile(buildScriptJarFile, pluginUrls)
            result.addAll(buildScriptInfo.projects)
        }
        return result
    }

    private fun maybeCompileBuildFile(buildFile: BuildFile, buildScriptJarFile: File, pluginUrls: List<URL>) {
        log(2, "Running build file ${buildFile.name} jar: $buildScriptJarFile")

        if (buildFile.exists() && buildScriptJarFile.exists()
                && buildFile.lastModified < buildScriptJarFile.lastModified()) {
            log(2, "Build file is up to date")
        } else {
            log(2, "Need to recompile ${buildFile.name}")

            kotlinCompilePrivate {
                classpath(files.kobaltJar)
                classpath(pluginUrls.map { it.file })
                sourceFiles(listOf(buildFile.path.toFile().absolutePath))
                output = buildScriptJarFile.absolutePath
            }.compile()
        }
    }

    /**
     * Generate the script file with only the plugins()/repos() directives and run it. Then return
     * the URL's of all the plug-ins that were found.
     */
    private fun findPlugInUrls(buildFile: BuildFile): List<URL> {
        val result = arrayListOf<URL>()
        val pluginCode = arrayListOf(
                "import com.beust.kobalt.*",
                "import com.beust.kobalt.api.*"
        )
        var parenCount = 0
        buildFile.path.toFile().forEachLine(Charset.defaultCharset()) { line ->
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

        //
        // Compile to preBuildScript.jar
        //
        val buildScriptJar = KFiles.findBuildScriptLocation(buildFile, "preBuildScript.jar")
        val buildScriptJarFile = File(buildScriptJar)
        buildScriptJarFile.parentFile.mkdirs()
        generateJarFile(BuildFile(Paths.get(pluginSourceFile.path), "Plugins"), buildScriptJarFile)

        //
        // Run preBuildScript.jar to initialize plugins and repos
        //
        parseBuildScriptJarFile(buildScriptJarFile, arrayListOf<URL>())

        //
        // All the plug-ins are now in Plugins.dynamicPlugins, download them if they're not already
        //
        Plugins.dynamicPlugins.forEach {
            result.add(it.jarFile.get().toURI().toURL())
        }

        return result
    }

    private fun generateJarFile(buildFile: BuildFile, buildScriptJarFile: File) {
        kotlinCompilePrivate {
            classpath(files.kobaltJar)
            sourceFiles(buildFile.path.toFile().absolutePath)
            output = buildScriptJarFile.absolutePath
        }.compile()
    }

    class BuildScriptInfo(val projects: List<Project>, val classLoader: ClassLoader)

    private fun parseBuildScriptJarFile(buildScriptJarFile: File, urls: List<URL>) : BuildScriptInfo {
        val projects = arrayListOf<Project>()
        var stream : InputStream? = null
        val allUrls = arrayListOf<URL>().plus(urls).plus(arrayOf(
                buildScriptJarFile.toURI().toURL(),
                File(files.kobaltJar).toURI().toURL()))
            .toTypedArray()
        val classLoader = URLClassLoader(allUrls)

        //
        // Install all the plugins
        //
        plugins.installPlugins(Plugins.dynamicPlugins, classLoader)

        try {
            stream = JarInputStream(FileInputStream(buildScriptJarFile))
            var entry = stream.nextJarEntry

            val classes = hashSetOf<Class<*>>()
            while (entry != null) {
                val name = entry.name;
                if (name.endsWith(".class")) {
                    val className = name.substring(0, name.length() - 6).replace("/", ".")
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
                        val r = method.invoke(null)
                        if (r is Project) {
                            log(2, "Found project $r in class $cls")
                            projects.add(r)
                        }
                    } else {
                        val taskAnnotation = method.getAnnotation(Task::class.java)
                        if (taskAnnotation != null) {
                            Plugins.defaultPlugin.methodTasks.add(Plugin.MethodTask(method, taskAnnotation))
                        }

                    }}
            }
        } finally {
            stream?.close()
        }

        // Now that we all the projects, sort them topologically
        val result = BuildScriptInfo(Kobalt.sortProjects(projects), classLoader)
        observable.onNext(result)
        return result
    }
}
