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
import com.beust.kobalt.plugin.packaging.PackagingPlugin
import com.google.common.collect.HashMultimap
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
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

@Singleton
public class AndroidPlugin @Inject constructor(val javaCompiler: JavaCompiler) : BasePlugin(), IClasspathContributor {
    override val name = "android"

    lateinit var context: KobaltContext

    override fun apply(project: Project, context: KobaltContext) {
        log(1, "Applying plug-in Android on project $project")
        this.context = context
        if (accept(project)) {
            project.compileDependencies.add(FileDependency(androidJar(project).toString()))
        }
        context.pluginInfo.classpathContributors.add(this)

        // TODO: Find a more flexible way of enabling this, e.g. creating a contributor for it
        (Kobalt.findPlugin("java") as JvmCompilerPlugin).addCompilerArgs("-target", "1.6", "-source", "1.6")
    }

    val configurations = hashMapOf<String, AndroidConfig>()

    fun setConfiguration(p: Project, config: AndroidConfig) {
        configurations.put(p.name!!, config)
    }

    override fun accept(project: Project) = configurations.containsKey(project.name!!)

    val flavor = "debug"

    fun compileSdkVersion(project: Project) = configurations[project.name!!]?.compileSdkVersion
    fun buildToolsVersion(project: Project) : String {
        val version = configurations[project.name!!]?.buildToolsVersion
        if (OperatingSystem.current().isWindows()) {
            return "build-tools-$version"
        } else {
            return version as String
        }
    }

    fun androidHome(project: Project) : String {
        var result = configurations[project.name!!]?.androidHome
        if (result == null) {
            result = System.getenv("ANDROID_HOME")
            if (result == null) {
                throw IllegalArgumentException("Neither androidHome nor \$ANDROID_HOME were defined")
            }
        }
        return result
    }

    fun androidJar(project: Project) : Path =
            Paths.get(androidHome(project), "platforms", "android-${compileSdkVersion(project)}", "android.jar")

    private fun generated(project: Project) = Paths.get(project.buildDirectory, "app", "build", "generated")
    private fun intermediates(project: Project) = Paths.get(project.buildDirectory, "app", "build", "intermediates")

    private fun aapt(project: Project) = "${androidHome(project)}/build-tools/${buildToolsVersion(project)}/aapt"

    private fun temporaryApk(project: Project, flavor: String)
            = KFiles.joinFileAndMakeDir(project.buildDirectory!!, "intermediates", "res", "resources-$flavor.ap_")

    private fun apk(project: Project, flavor: String)
            = KFiles.joinFileAndMakeDir(project.buildDirectory!!, "outputs", "apk" ,"app-$flavor.apk")

    @Task(name = "generateR", description = "Generate the R.java file",
            runBefore = arrayOf("compile"), runAfter = arrayOf("clean"))
    fun taskGenerateRFile(project: Project) : TaskResult {

        val generated = generated(project)
        explodeAarFiles(project, generated)
        generateR(project, generated, aapt(project))
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

    private fun generateR(project: Project, generated: Path, aapt: String) {
        val compileSdkVersion = compileSdkVersion(project)
        val androidJar = Paths.get(androidHome(project), "platforms", "android-$compileSdkVersion", "android.jar")
        val applicationId = configurations[project.name!!]?.applicationId!!
        val manifestDir = Paths.get(project.directory, "app", "src", "main").toString()
        val manifest = Paths.get(manifestDir, "AndroidManifest.xml")

        val crunchedPngDir = KFiles.joinAndMakeDir(intermediates(project).toString(), "res", flavor)

        AaptCommand(project, aapt, "crunch").call(listOf(
                "-v",
                "-S", "app/src/main/res",
                "-C", crunchedPngDir
        ))

        AaptCommand(project, aapt, "package").call(listOf(
                "-f",
                "--no-crunch",
                "-I", androidJar.toString(),
                "-M", manifest.toString(),
                "-S", crunchedPngDir,
                "-S", "app/src/main/res",
                // where to find more assets
                "-A", KFiles.joinAndMakeDir(intermediates(project).toString(), "assets", flavor),
                "-m",  // create directory
                // where all gets generated
                "-J", KFiles.joinAndMakeDir(generated.toString(), "sources", "r", flavor).toString(),
                "-F", temporaryApk(project, flavor),
                "--debug-mode",
                "-0", "apk",
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
    private fun explodeAarFiles(project: Project, outputDir: Path) {
        project.compileDependencies.filter {
            it.jarFile.get().name.endsWith(".aar")
        }.forEach {
            log(2, "Exploding ${it.jarFile.get()}")
            val mavenId = MavenId(it.id)
            val destDir = Paths.get(outputDir.toFile().absolutePath, mavenId.artifactId, mavenId.version).toFile()
            JarUtils.extractJarFile(it.jarFile.get(), destDir)
            val classesJar = Paths.get(destDir.absolutePath, "classes.jar")
            classpathEntries.put(project.name, FileDependency(classesJar.toFile().absolutePath))
        }
    }

    private fun compile(project: Project, rDirectory: String) : File {
        val sourceFiles = arrayListOf(Paths.get(rDirectory, "R.java").toFile().path)
        val buildDir = Paths.get(project.buildDirectory, "generated", "classes").toFile()

        javaCompiler.compile(project, context, listOf(), sourceFiles, buildDir, listOf())
        return buildDir
    }

    companion object {
        const val TASK_GENERATE = "generateDex"
    }

    @Task(name = TASK_GENERATE, description = "Generate the dex file", alwaysRunAfter = arrayOf("compile"))
    fun taskGenerateDex(project: Project) : TaskResult {
        //
        // Call dx to generate classes.dex
        //
        val buildToolsDir = buildToolsVersion(project)
        val dx = "${androidHome(project)}/build-tools/$buildToolsDir/dx" +
            if (OperatingSystem.current().isWindows()) ".bat" else ""
        val buildDir = context.pluginProperties.get("java", JvmCompilerPlugin.BUILD_DIR)
        val libsDir = (context.pluginProperties.get("packaging", PackagingPlugin.LIBS_DIR) as File).path
        File(libsDir.toString()).mkdirs()
        val classesDex = "classes.dex"
        val classesDexDir = KFiles.joinAndMakeDir(libsDir, "intermediates", "dex", flavor)
        val outClassesDex = KFiles.joinDir(classesDexDir, classesDex)

        RunCommand(dx).run(listOf("--dex", "--output", outClassesDex, buildDir!!.toString()))

        //
        // Add classes.dex to existing .ap_
        //
        AaptCommand(project, aapt(project), "add").apply {
            directory = File(outClassesDex).parentFile
        }.call(listOf("-v", KFiles.joinDir("../../../../..", temporaryApk(project, flavor)), classesDex))

        return TaskResult()
    }


    /**
     * Sign the apk
     * Mac:
     * jarsigner -keystore ~/.android/debug.keystore -storepass android -keypass android -signedjar a.apk a.ap_
     * androiddebugkey
     */
    @Task(name = "signApk", description = "Sign the apk file", runAfter = arrayOf(TASK_GENERATE),
            runBefore = arrayOf("assemble"))
    fun signApk(project: Project) : TaskResult {
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

    override fun entriesFor(project: Project?): Collection<IClasspathDependency> {
        if (project != null) {
            return classpathEntries.get(project.name!!) ?: listOf()
        } else {
            return listOf()
        }
    }

}
