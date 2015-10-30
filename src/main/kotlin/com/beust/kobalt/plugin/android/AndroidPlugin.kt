package com.beust.kobalt.plugin.android

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
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
import java.nio.file.Path
import java.nio.file.Paths

class AndroidConfiguration(var compileSdkVersion : String = "23",
       var buildToolsVersion : String = "23.0.1",
       var applicationId: String? = null)

@Directive
fun Project.android(init: AndroidConfiguration.() -> Unit) : AndroidConfiguration {
    val pd = AndroidConfiguration()
    pd.init()
    (Kobalt.findPlugin("android") as AndroidPlugin).setConfiguration(this, pd)
    return pd
}

@Singleton
public class AndroidPlugin @Inject constructor(val javaCompiler: JavaCompiler) : BasePlugin(), IClasspathContributor {
    val ANDROID_HOME = "/Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk"
    override val name = "android"

    var context: KobaltContext? = null

    override fun apply(project: Project, context: KobaltContext) {
        log(1, "Applying plug-in Android on project $project")
        this.context = context
        if (accept(project)) {
            project.compileDependencies.add(FileDependency(androidJar(project).toString()))
        }
        context.classpathContributors.add(this)
    }

    val configurations = hashMapOf<String, AndroidConfiguration>()

    fun setConfiguration(p: Project, configuration: AndroidConfiguration) {
        configurations.put(p.name!!, configuration)
    }

    override fun accept(project: Project) = configurations.containsKey(project.name!!)

    fun dirGet(dir: Path, vararg others: String) : String {
        val result = Paths.get(dir.toString(), *others)
        with(result.toFile()) {
            deleteRecursively()
            mkdirs()
        }
        return result.toString()
    }

    fun compileSdkVersion(project: Project) = configurations[project.name!!]?.compileSdkVersion
    fun buildToolsVersion(project: Project) = configurations[project.name!!]?.buildToolsVersion

    fun androidJar(project: Project) : Path =
            Paths.get(ANDROID_HOME, "platforms", "android-${compileSdkVersion(project)}", "android.jar")

    @Task(name = "generateR", description = "Generate the R.java file", runBefore = arrayOf("compile"))
    fun taskGenerateRFile(project: Project) : TaskResult {

        val flavor = "debug"
        val compileSdkVersion = compileSdkVersion(project)
        val buildToolsVersion = buildToolsVersion(project)
        val androidJar = Paths.get(ANDROID_HOME, "platforms", "android-$compileSdkVersion", "android.jar")
        val applicationId = configurations[project.name!!]?.applicationId!!
        val intermediates = Paths.get(project.directory, "app", "build", "intermediates")
        val manifestDir = Paths.get(project.directory, "app", "src", "main").toString()
//        val manifestIntermediateDir = dirGet(intermediates, "manifests", "full", flavor)
        val manifest = Paths.get(manifestDir, "AndroidManifest.xml")
        val generated = Paths.get(project.directory, "app", "build", "generated")
        val aapt = "$ANDROID_HOME/build-tools/$buildToolsVersion/aapt"
        val outputDir = dirGet(intermediates, "resources", "resources-$flavor")

        explodeAarFiles(project, generated)

        val crunchedPngDir = dirGet(intermediates, "res", flavor)
        RunCommand(aapt).apply {
            directory = File(project.directory)
        }.run(arrayListOf(
                "crunch",
                "-v",
                "-S", "app/src/main/res",
                "-C", crunchedPngDir
        ))

        RunCommand(aapt).apply {
            directory = File(project.directory)
        }.run(arrayListOf(
                "package",
                "-f",
                "--no-crunch",
                "-I", androidJar.toString(),
                "-M", manifest.toString(),
                "-S", crunchedPngDir,
                "-S", "app/src/main/res",
                "-A", dirGet(intermediates, "assets", flavor), // where to find more assets
                "-m",  // create directory
                "-J", dirGet(generated, "sources", "r", flavor).toString(), // where all gets generated
                "-F", Paths.get(outputDir, "resources-debug.ap_").toString(),
                "--debug-mode",
                "-0", "apk",
                "--custom-package", applicationId,
                "--output-text-symbols", dirGet(intermediates, "symbol", flavor))
        )

        val rDirectory = KFiles.joinDir(generated.toFile().path, "sources", "r", flavor,
                applicationId.replace(".", File.separator))
        val generatedBuildDir = compile(project, rDirectory)
        project.compileDependencies.add(FileDependency(generatedBuildDir.path))
        return TaskResult()
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

        javaCompiler.compile(project, context, listOf(), sourceFiles, buildDir.absolutePath, listOf())
        return buildDir
    }

    val classpathEntries = HashMultimap.create<String, IClasspathDependency>()

    override fun entriesFor(project: Project): Collection<IClasspathDependency> {
        return classpathEntries.get(project.name!!) ?: listOf()
    }

}


/*
/Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk/build-tools/21.1.2/aapt package
-f
--no-crunch
-I /Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk/platforms/android-22/android.jar
-M /Users/beust/kotlin/kotlin-android-example/app/build/intermediates/manifests/full/debug/AndroidManifest.xml
-S /Users/beust/kotlin/kotlin-android-example/app/build/intermediates/res/debug
-A /Users/beust/kotlin/kotlin-android-example/app/build/intermediates/assets/debug
-m
-J /Users/beust/kotlin/kotlin-android-example/app/build/generated/source/r/debug
-F /Users/beust/kotlin/kotlin-android-example/app/build/intermediates/resources/resources-debug.ap_ --debug-mode --custom-package com.beust.example
-0 apk
--output-text-symbols /Users/beust/kotlin/kotlin-android-example/app/build/intermediates/symbols/debug
*/
