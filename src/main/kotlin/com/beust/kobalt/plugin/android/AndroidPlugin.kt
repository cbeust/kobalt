package com.beust.kobalt.plugin.android

import com.beust.kobalt.OperatingSystem
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.FileDependency
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.RunCommand
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.java.JavaCompiler
import com.beust.kobalt.plugin.packaging.JarUtils
import com.google.common.collect.HashMultimap
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

class AndroidConfig(var compileSdkVersion : String = "23",
        var buildToolsVersion : String = "23.0.1",
        var applicationId: String? = null,
        val androidHome: String? = null)

@Directive
fun Project.android(init: AndroidConfig.() -> Unit) : AndroidConfig {
    val pd = AndroidConfig()
    pd.init()
    (Kobalt.findPlugin("android") as AndroidPlugin).setConfiguration(this, pd)
    return pd
}

val Project.isAndroid : Boolean
        get() = (Kobalt.findPlugin("android") as AndroidPlugin).isAndroid(this)

@Singleton
public class AndroidPlugin @Inject constructor(val javaCompiler: JavaCompiler)
        : BasePlugin(), IClasspathContributor, IRepoContributor {
    override val name = "android"

    fun isAndroid(project: Project) = configurations[project.name] != null

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        log(1, "Applying plug-in Android on project $project")
        if (accept(project)) {
            project.compileDependencies.add(FileDependency(androidJar(project).toString()))
        }
        context.pluginInfo.classpathContributors.add(this)

        // TODO: Find a more flexible way of enabling this, e.g. creating a contributor for it
        (Kobalt.findPlugin("java") as JvmCompilerPlugin).addCompilerArgs("-target", "1.6", "-source", "1.6")
    }

    val configurations = hashMapOf<String, AndroidConfig>()

    fun setConfiguration(p: Project, config: AndroidConfig) {
        configurations.put(p.name, config)
    }

    override fun accept(project: Project) = configurations.containsKey(project.name)

    val flavor = "debug"

    fun compileSdkVersion(project: Project) = configurations[project.name]?.compileSdkVersion
    fun buildToolsVersion(project: Project): String {
        val version = configurations[project.name]?.buildToolsVersion
        if (OperatingSystem.current().isWindows() && version == "21.1.2")
            return "build-tools-$version"
        else
            return version as String
    }

    fun androidHomeNoThrows(project: Project?): String? {
        var result = System.getenv("ANDROID_HOME")
        if (project != null) {
            configurations[project.name]?.androidHome?.let {
                result = it
            }
        }

        return result
    }

    fun androidHome(project: Project?) = androidHomeNoThrows(project) ?:
            throw IllegalArgumentException("Neither androidHome nor \$ANDROID_HOME were defined")

    fun androidJar(project: Project): Path =
            Paths.get(androidHome(project), "platforms", "android-${compileSdkVersion(project)}", "android.jar")

    private fun generated(project: Project) = Paths.get(project.buildDirectory, "generated")
    private fun intermediates(project: Project) = Paths.get(project.buildDirectory, "intermediates")

    private fun aapt(project: Project) = "${androidHome(project)}/build-tools/${buildToolsVersion(project)}/aapt"

    private fun temporaryApk(project: Project, flavor: String)
            = KFiles.joinFileAndMakeDir(intermediates(project).toFile().path, "resources", "resources-$flavor.ap_")

    private fun apk(project: Project, flavor: String)
            = KFiles.joinFileAndMakeDir(project.buildDirectory!!, "outputs", "apk", "app-$flavor.apk")

    @Task(name = "generateR", description = "Generate the R.java file",
            runBefore = arrayOf("compile"), runAfter = arrayOf("clean"))
    fun taskGenerateRFile(project: Project): TaskResult {

        val intermediates = intermediates(project)
        val resDir = KFiles.joinDir(intermediates.toFile().path, "res", flavor)
        explodeAarFiles(project, intermediates, File(resDir))
        val generated = generated(project)
        generateR(project, generated, aapt(project), resDir)
        return TaskResult()
    }

    open class AndroidCommand(androidHome: String, command: String) : RunCommand(command) {
        init {
            env.put("ANDROID_HOME", androidHome)
        }
    }

    inner class AaptCommand(project: Project, aapt: String, val aaptCommand: String,
            cwd: File = File(project.directory)) : AndroidCommand(androidHome(project), aapt) {
        init {
            directory = cwd
        }

        fun call(args: List<String>) = run(arrayListOf(aaptCommand) + args)
    }

    private fun generateR(project: Project, generated: Path, aapt: String, resDir: String) {
        val compileSdkVersion = compileSdkVersion(project)
        val androidJar = Paths.get(androidHome(project), "platforms", "android-$compileSdkVersion", "android.jar")
        val applicationId = configurations[project.name]?.applicationId!!
        val manifestDir = Paths.get(project.directory, "app", "src", "main").toString()
        val manifest = Paths.get(manifestDir, "AndroidManifest.xml")

        val crunchedPngDir = KFiles.joinAndMakeDir(intermediates(project).toString(), "res", flavor)

        AaptCommand(project, aapt, "crunch").call(listOf(
                "-v",
                "-S", "app/src/main/res",//resourceDir,
                "-S", resDir,
                "-C", crunchedPngDir
        ))

        AaptCommand(project, aapt, "package").call(listOf(
                "-f",
                "--no-crunch",
                "-I", androidJar.toString(),
                "-M", manifest.toString(),
                "-S", "app/src/main/res",
                "-S", resDir,
                // where to find more assets
                "-A", KFiles.joinAndMakeDir(intermediates(project).toString(), "assets", flavor),
                "-m", // create directory
                // where all gets generated
                "-J", KFiles.joinAndMakeDir(generated.toString(), "sources", "r", flavor).toString(),
                "-F", temporaryApk(project, flavor),
                "--debug-mode",
                "-0", "apk",
                "--auto-add-overlay",
                "--custom-package", applicationId,
                "--output-text-symbols", KFiles.joinAndMakeDir(intermediates(project).toString(), "symbol", flavor))
        )

        val rDirectory = KFiles.joinDir(generated.toFile().path, "sources", "r", flavor,
                applicationId.replace(".", File.separator))
        val generatedBuildDir = compile(project, rDirectory)
        project.compileDependencies.add(FileDependency(generatedBuildDir.path))
    }

    /**
     * Extract all the .aar files found in the dependencies and add the android.jar to classpathEntries,
     * which will be added to the classpath at compile time
     */
    private fun explodeAarFiles(project: Project, outputDir: Path, resDir: File) {
        project.compileDependencies.filter {
            it.jarFile.get().name.endsWith(".aar")
        }.forEach {
            val mavenId = MavenId(it.id)
            val destDir = Paths.get(outputDir.toFile().absolutePath, "exploded-aar", mavenId.groupId,
                    mavenId.artifactId, mavenId.version)
                    .toFile()
            log(2, "Exploding ${it.jarFile.get()} to $destDir")
            JarUtils.extractJarFile(it.jarFile.get(), destDir)
            val classesJar = Paths.get(destDir.absolutePath, "classes.jar")

            // Add the classses.jar of this .aar to the classpath entries (which are returned via IClasspathContributor)
            classpathEntries.put(project.name, FileDependency(classesJar.toFile().absolutePath))
            // Also add all the jar files found in the libs/ directory
            File(destDir, "libs").let { libsDir ->
                if (libsDir.exists()) {
                    libsDir.listFiles().filter { it.name.endsWith(".jar")}.forEach {
                        classpathEntries.put(project.name, FileDependency(it.absolutePath))
                    }
                }
            }

            // Copy all the resources from this aar into the same intermediate directory
            log(2, "Copying the resources to $resDir")
            KFiles.copyRecursively(destDir.resolve("res"), resDir, deleteFirst = false)
        }
    }

    private fun compile(project: Project, rDirectory: String): File {
        val sourceFiles = arrayListOf(Paths.get(rDirectory, "R.java").toFile().path)
        val buildDir = Paths.get(project.buildDirectory, "generated", "classes").toFile()

        javaCompiler.compile(project, context, listOf(), sourceFiles, buildDir, listOf(
                "-source", "1.6", "-target", "1.6"
        ))
        return buildDir
    }

    companion object {
        const val TASK_GENERATE_DEX = "generateDex"
    }

    @Task(name = TASK_GENERATE_DEX, description = "Generate the dex file", runAfter = arrayOf("compile"))
    fun taskGenerateDex(project: Project): TaskResult {
        //
        // Call dx to generate classes.dex
        //
        val buildToolsDir = buildToolsVersion(project)
        val dx = "${androidHome(project)}/build-tools/$buildToolsDir/dx" +
                if (OperatingSystem.current().isWindows()) ".bat" else ""
        val classesDexDir = KFiles.joinDir(intermediates(project).toFile().path, "dex", flavor)
        File(classesDexDir).mkdirs()
        val classesDex = "classes.dex"
        val outClassesDex = KFiles.joinDir(classesDexDir, classesDex)

        val args = listOf("--dex", "--output", outClassesDex)
        val otherArgs =
            project.dependencies?.let {
                it.dependencies.map {
                    it.jarFile.get().path
                }.filter { ! it.endsWith(".aar") && ! it.endsWith("android.jar") }
            } ?: emptyList()
        RunCommand(dx).run(args + otherArgs)

        //
        // Add classes.dex to existing .ap_
        // Because aapt doesn't handle directory moving, we need to cd to classes.dex's directory so
        // that classes.dex ends up in the root directory of the .ap_.
        //
        AaptCommand(project, aapt(project), "add").apply {
            directory = File(outClassesDex).parentFile
        }.call(listOf("-v", KFiles.joinDir(File(temporaryApk(project, flavor)).absolutePath), classesDex))

        return TaskResult()
    }


    /**
     * Sign the apk
     * Mac:
     * jarsigner -keystore ~/.android/debug.keystore -storepass android -keypass android -signedjar a.apk a.ap_
     * androiddebugkey
     */
    @Task(name = "signApk", description = "Sign the apk file", runAfter = arrayOf(TASK_GENERATE_DEX),
            runBefore = arrayOf("assemble"))
    fun signApk(project: Project): TaskResult {
        val apk = apk(project, flavor)
        val temporaryApk = temporaryApk(project, flavor)
        RunCommand("jarsigner").run(listOf(
                "-keystore", homeDir(".android", "debug.keystore"),
                "-storepass", "android",
                "-keypass", "android",
                "-signedjar", apk,
                temporaryApk,
                "androiddebugkey"
        ))
        log(1, "Created $apk")
        return TaskResult()
    }

    private val classpathEntries = HashMultimap.create<String, IClasspathDependency>()

    // IClasspathContributor
    override fun entriesFor(project: Project?): Collection<IClasspathDependency> {
        return if (project != null) {
            classpathEntries.get(project.name) ?: emptyList()
        } else {
            emptyList()
        }
    }

    // IRepoContributor
    override fun reposFor(project: Project?): List<URI> {
        val home = androidHomeNoThrows(project)
        return if (home != null) {
            listOf(Paths.get(KFiles.joinDir(home, "extras", "android", "m2repository")).toUri())
        } else {
            emptyList()
        }
    }
}
