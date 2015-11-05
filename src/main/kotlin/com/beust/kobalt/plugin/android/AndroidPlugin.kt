package com.beust.kobalt.plugin.android

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
       var applicationId: String? = null)

@Directive
fun Project.android(init: AndroidConfig.() -> Unit) : AndroidConfig {
    val pd = AndroidConfig()
    pd.init()
    (Kobalt.findPlugin("android") as AndroidPlugin).setConfiguration(this, pd)
    return pd
}

@Singleton
public class AndroidPlugin @Inject constructor(val javaCompiler: JavaCompiler) : BasePlugin(), IClasspathContributor {
    val ANDROID_HOME = "/Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk"
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

    fun dirGet(dir: Path, vararg others: String) : String {
        val result = Paths.get(dir.toString(), *others)
        with(result.toFile()) {
            mkdirs()
        }
        return result.toString()
    }

    val flavor = "debug"

    fun compileSdkVersion(project: Project) = configurations[project.name!!]?.compileSdkVersion
    fun buildToolsVersion(project: Project) = configurations[project.name!!]?.buildToolsVersion

    fun androidJar(project: Project) : Path =
            Paths.get(ANDROID_HOME, "platforms", "android-${compileSdkVersion(project)}", "android.jar")

    fun generated(project: Project) = Paths.get(project.directory, "app", "build", "generated")

    private fun aapt(project: Project) = "$ANDROID_HOME/build-tools/${buildToolsVersion(project)}/aapt"

    private fun temporaryApk(project: Project, flavor: String) = apk(project, flavor, "ap_")

    private fun apk(project: Project, flavor: String, suffix: String) : String {
        val outputDir = dirGet(intermediates(project), "resources", "resources-$flavor")
        return Paths.get(outputDir, "resources-$flavor.$suffix").toString()
    }

    private fun intermediates(project: Project) = Paths.get(project.directory, "app", "build", "intermediates")

    @Task(name = "generateR", description = "Generate the R.java file",
            runBefore = arrayOf("compile"), runAfter = arrayOf("clean"))
    fun taskGenerateRFile(project: Project) : TaskResult {

        val generated = generated(project)
        explodeAarFiles(project, generated)
        generateR(project, generated, aapt(project))
        return TaskResult()
    }

    class AaptCommand(project: Project, aapt: String, val aaptCommand: String,
            cwd: File = File(project.directory)) : RunCommand(aapt) {
        init {
            directory = cwd
        }
        fun call(args: List<String>) = run(arrayListOf(aaptCommand) + args)
    }

    private fun generateR(project: Project, generated: Path, aapt: String) {
        val compileSdkVersion = compileSdkVersion(project)
        val androidJar = Paths.get(ANDROID_HOME, "platforms", "android-$compileSdkVersion", "android.jar")
        val applicationId = configurations[project.name!!]?.applicationId!!
        val manifestDir = Paths.get(project.directory, "app", "src", "main").toString()
        val manifest = Paths.get(manifestDir, "AndroidManifest.xml")

        val crunchedPngDir = dirGet(intermediates(project), "res", flavor)

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
                "-A", dirGet(intermediates(project), "assets", flavor), // where to find more assets
                "-m",  // create directory
                "-J", dirGet(generated, "sources", "r", flavor).toString(), // where all gets generated
                "-F", temporaryApk(project, flavor),
                "--debug-mode",
                "-0", "apk",
                "--custom-package", applicationId,
                "--output-text-symbols", dirGet(intermediates(project), "symbol", flavor))
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

    @Task(name = "generateDex", description = "Generate the dex file", alwaysRunAfter = arrayOf("compile"))
    fun taskGenerateDex(project: Project) : TaskResult {
        //
        // Call dx to generate classes.dex
        //
        val buildToolsDir = buildToolsVersion(project)
        val dx = "$ANDROID_HOME/build-tools/$buildToolsDir/dx"
        val buildDir = context.pluginProperties.get("java", JvmCompilerPlugin.BUILD_DIR)
        val libsDir = context.pluginProperties.get("packaging", PackagingPlugin.LIBS_DIR)
        File(libsDir!!.toString()).mkdirs()
        val classesDex = "classes.dex"
        val outClassesDex = KFiles.joinDir(libsDir.toString(), classesDex)
        val relClassesDex = File(outClassesDex).parentFile
        RunCommand(dx).run(listOf("--dex", "--output", outClassesDex,
                buildDir!!.toString()))


        //
        // Add classes.dex to existing .ap_
        //
        AaptCommand(project, aapt(project), "add", relClassesDex).call(listOf(
                "-v", temporaryApk(project, flavor), classesDex
        ))
        return TaskResult()
    }


    /**
     * Sign the apk
     * Mac:
     * jarsigner -keystore ~/.android/debug.keystore -storepass android -keypass android -signedjar a.apk a.ap_
     * androiddebugkey
     */
    @Task(name = "signApk", description = "Generate the dex file", runAfter = arrayOf("generateDex"),
            runBefore = arrayOf("assemble"))
    fun signApk(project: Project) : TaskResult {
        val apk = apk(project, flavor, "apk")
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

    override fun entriesFor(project: Project): Collection<IClasspathDependency> {
        return classpathEntries.get(project.name!!) ?: listOf()
    }

}
