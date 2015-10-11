package com.beust.kobalt.kotlin

import com.beust.kobalt.Plugins
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Plugin
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.maven.KobaltException
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.plugin.kotlin.kotlinCompilePrivate
import com.google.inject.assistedinject.Assisted
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.util.jar.JarInputStream
import javax.inject.Inject
import kotlin.properties.Delegates

/**
 * Compile Build.kt into a jar file
 */
public class ScriptCompiler @Inject constructor(
        @Assisted("jarFiles") val jarFiles: List<String>,
        @Assisted("instantiate") val instantiate: (ClassLoader, String) -> Class<*>,
        val files: KFiles) : KobaltLogger {

    interface IFactory {
        fun create(@Assisted("jarFiles") jarFiles: List<String>,
                @Assisted("instantiate") instantiate: (ClassLoader, String) -> Class<*>) : ScriptCompiler
    }

    private var buildScriptJarFile by Delegates.notNull<File>()

    public class CompileOutput(val projects: List<Project>, val plugins: List<String>, val classLoader: ClassLoader)

    public fun compile(buildFile: BuildFile, lastModified: Long, jarFileName: String) : CompileOutput {

        if (! buildFile.exists()) {
            throw KobaltException("Couldn't find ${buildFile.name}")
        }

        buildScriptJarFile = File(jarFileName)
        buildScriptJarFile.parentFile.mkdirs()

        log(2, "Running build file ${buildFile.name} jar: ${buildScriptJarFile}")

        if (buildFile.exists() && buildScriptJarFile.exists()
                && lastModified < buildScriptJarFile.lastModified()) {
            log(2, "Build file is up to date")
        } else {
            log(2, "Need to recompile ${buildFile.name}")
            generateJarFile(buildFile)
        }
        val pi = instantiateBuildFile()
        return CompileOutput(pi.projects, arrayListOf<String>(), pi.classLoader)
    }

    private fun generateJarFile(buildFile: BuildFile) {
        kotlinCompilePrivate {
            classpath(files.kobaltJar)
            classpath(jarFiles)
            sourceFiles(buildFile.path.toFile().absolutePath)
            output = buildScriptJarFile.absolutePath
        }.compile()
    }

    class  ProjectInfo(val projects: List<Project>, val classLoader: ClassLoader)

    private fun instantiateBuildFile() : ProjectInfo {
        val result = arrayListOf<Project>()
        var stream : InputStream? = null
        val classLoader = URLClassLoader(arrayOf(buildScriptJarFile.toURI().toURL()))
        try {
            log(1, "!!!!!!!!! CREATED CLASSLOADER FOR buildScriptJarFile: $classLoader")
            stream = JarInputStream(FileInputStream(buildScriptJarFile))
            var entry = stream.nextJarEntry

            val classes = hashSetOf<Class<*>>()
            while (entry != null) {
                val name = entry.name;
                if (name.endsWith(".class")) {
                    val className = name.substring(0, name.length() - 6).replace("/", ".")
                    var cl : Class<*>? = instantiate(classLoader, className)
                    if (cl != null) {
                        classes.add(cl)
                    } else {
                        throw KobaltException("Couldn't instantiate ${className}")
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
                            log(2, "Found project ${r} in class ${cls}")
                            result.add(r)
                        }
                    } else {
                        val taskAnnotation = method.getAnnotation(Task::class.java)
                        if (taskAnnotation != null) {
//                            Plugins.defaultPlugin.addTask(taskAnnotation, )
                            Plugins.defaultPlugin.methodTasks.add(Plugin.MethodTask(method, taskAnnotation))
                        }

                    }}
//                cls.methods.filter { method ->
//                    method.getName().startsWith("get") && Modifier.isStatic(method.getModifiers())
//                }.forEach {
//                    val r = it.invoke(null)
//                    if (r is Project) {
//                        log(2, "Found project ${r} in class ${cls}")
//                        result.add(r)
//                    }
//                }
            }
        } finally {
            stream?.close()
        }

        // Now that we all the projects, sort them topologically
        return ProjectInfo(Kobalt.sortProjects(result), classLoader)
    }
}

