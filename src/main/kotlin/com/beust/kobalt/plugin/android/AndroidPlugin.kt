package com.beust.kobalt.plugin.android

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.CompilerActionInfo
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.FileDependency
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.RunCommand
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.java.JavaCompiler
import com.beust.kobalt.plugin.java.JavaProject
import com.beust.kobalt.plugin.packaging.JarUtils
import com.google.common.collect.HashMultimap
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class AndroidConfig(var compileSdkVersion : String = "23",
        var buildToolsVersion : String = "23.0.1",
        var applicationId: String? = null,
        val androidHome: String? = null)

@Directive
fun Project.android(init: AndroidConfig.() -> Unit) : AndroidConfig {
    val pd = AndroidConfig()
    pd.init()
    (Kobalt.findPlugin("android") as AndroidPlugin).addConfiguration(this, pd)
    return pd
}

//val Project.isAndroid : Boolean
//        get() = (Kobalt.findPlugin("android") as AndroidPlugin).isAndroid(this)

@Singleton
public class AndroidPlugin @Inject constructor(val javaCompiler: JavaCompiler)
        : ConfigPlugin<AndroidConfig>(), IClasspathContributor, IRepoContributor, ICompilerFlagContributor,
            ICompilerInterceptor, ISourceDirectoriesIncerceptor, IBuildDirectoryIncerceptor {
    override val name = "android"

    fun isAndroid(project: Project) = configurationFor(project) != null

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        log(1, "Applying plug-in Android on project $project")
        if (accept(project)) {
            project.compileDependencies.add(FileDependency(androidJar(project).toString()))

            addVariantTasks(project, "generateR", runBefore = listOf("compile"),
                    runTask = { taskGenerateRFile(project) })
            addVariantTasks(project, "generateDex", runAfter = listOf("compile"), runBefore = listOf("assemble"),
                    runTask = { taskGenerateDex(project) })
            addVariantTasks(project, "signApk", runAfter = listOf("generateDex"), runBefore = listOf("assemble"),
                    runTask = { taskSignApk(project) })
            addVariantTasks(project, "install", runAfter = listOf("signApk"),
                    runTask = { taskInstall(project) })
        }
        context.pluginInfo.classpathContributors.add(this)

        // TODO: Find a more flexible way of enabling this, e.g. creating a contributor for it
//        (Kobalt.findPlugin("java") as JvmCompilerPlugin).addCompilerArgs(project, "-target", "1.6", "-source", "1.6")
    }


    override fun accept(project: Project) = isAndroid(project)

    fun compileSdkVersion(project: Project) = configurationFor(project)?.compileSdkVersion

    fun buildToolsVersion(project: Project): String {
        val version = configurationFor(project)?.buildToolsVersion
        if (OperatingSystem.current().isWindows() && version == "21.1.2")
            return "build-tools-$version"
        else
            return version as String
    }

    fun androidHomeNoThrows(project: Project?): String? {
        var result = System.getenv("ANDROID_HOME")
        if (project != null) {
            configurationFor(project)?.androidHome?.let {
                result = it
            }
        }

        return result
    }

    fun androidHome(project: Project?) = androidHomeNoThrows(project) ?:
            throw IllegalArgumentException("Neither androidHome nor \$ANDROID_HOME were defined")

    fun androidJar(project: Project): Path =
            Paths.get(androidHome(project), "platforms", "android-${compileSdkVersion(project)}", "android.jar")

    private fun aapt(project: Project) = "${androidHome(project)}/build-tools/${buildToolsVersion(project)}/aapt"

    private fun adb(project: Project) = "${androidHome(project)}/platform-tools/adb"

    private fun temporaryApk(project: Project, flavor: String)
            = KFiles.joinFileAndMakeDir(AndroidFiles.intermediates(project), "res", "resources-$flavor.ap_")

    private fun apk(project: Project, flavor: String)
            = KFiles.joinFileAndMakeDir(project.buildDirectory, "outputs", "apk", "app-$flavor.apk")

    @Task(name = "generateR", description = "Generate the R.java file",
            runBefore = arrayOf("compile"), runAfter = arrayOf("clean"))
    fun taskGenerateRFile(project: Project): TaskResult {

        val intermediates = AndroidFiles.intermediates(project)
        val resDir = "temporaryBogusResDir"
        explodeAarFiles(project, intermediates, File(resDir))
        val generated = AndroidFiles.generated(project)
        generateR(project, generated, aapt(project))
        return TaskResult()
    }

    inner class AaptCommand(project: Project, aapt: String, val aaptCommand: String,
            useErrorStream: Boolean = false,
            cwd: File = File(project.directory)) : AndroidCommand(project, androidHome(project), aapt) {
        init {
            directory = cwd
            useErrorStreamAsErrorIndicator = useErrorStream
        }

        override fun call(args: List<String>) = super.run(arrayListOf(aaptCommand) + args)
    }

    private fun generateR(project: Project, generated: String, aapt: String) {
        val compileSdkVersion = compileSdkVersion(project)
        val androidJar = Paths.get(androidHome(project), "platforms", "android-$compileSdkVersion", "android.jar")
        val applicationId = configurationFor(project)?.applicationId!!
        val intermediates = AndroidFiles.intermediates(project)
        val crunchedPngDir = KFiles.joinAndMakeDir(AndroidFiles.intermediates(project).toString(), "res")

//        AaptCommand(project, aapt, "crunch").call(listOf(
//                "-v",
//                "-C", mergedResources(project, context.variant),
//                "-S", crunchedPngDir
//        ))

        val variantDir = context.variant.toIntermediateDir()

        val rDirectory = KFiles.joinAndMakeDir(generated, "source", "r", variantDir).toString()
        AaptCommand(project, aapt, "package", false).call(listOf(
                "-f",
                "--no-crunch",
                "-I", androidJar.toString(),
                "-M", AndroidFiles.mergedManifest(project, context.variant),
                "-S", AndroidFiles.mergedResources(project, context.variant),
                // where to find more assets
                "-A", KFiles.joinAndMakeDir(intermediates, "assets", variantDir),
                "-m", // create directory
                // where all gets generated
                "-J", rDirectory,
                "-F", temporaryApk(project, context.variant.shortArchiveName),
                "--debug-mode",
                "-0", "apk",
                "--auto-add-overlay",
                "--custom-package", applicationId
         //       "--output-text-symbols", KFiles.joinAndMakeDir(intermediates(project).toString(), "symbol", flavor)
        ))

        val rOutputDirectory = KFiles.joinDir(rDirectory, applicationId.replace(".", File.separator))
        val generatedBuildDir = compile(project, rOutputDirectory)
        project.compileDependencies.add(FileDependency(generatedBuildDir.path))
    }

    /**
     * Extract all the .aar files found in the dependencies and add the android.jar to classpathEntries,
     * which will be added to the classpath at compile time
     */
    private fun explodeAarFiles(project: Project, outputDir: String, resDir: File) {
        project.compileDependencies.filter {
            it.jarFile.get().name.endsWith(".aar")
        }.forEach {
            val mavenId = MavenId(it.id)
            val destDir = Paths.get(outputDir, "exploded-aar", mavenId.groupId,
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
        val cai = CompilerActionInfo(project.directory, listOf(), sourceFiles, buildDir, listOf(
                "-source", "1.6", "-target", "1.6"))
        javaCompiler.compile(project, context, cai)
        return buildDir
    }

    /**
     * Implements ICompilerFlagContributor
     * Make sure we compile and generate 1.6 sources unless the build file defined those (which can
     * happen if the developer is using RetroLambda for example).
     */
    override fun flagsFor(project: Project) : List<String> {
        if (project is JavaProject) {
            val result: ArrayList<String> = project.projectProperties.get(JvmCompilerPlugin.COMPILER_ARGS)?.let {
                arrayListOf<String>().apply { addAll(it as List<String>) }
            } ?: arrayListOf<String>()
            if (!result.contains("-source")) with(result) {
                addAll(listOf("-source", "1.6"))
            }
            if (!result.contains("-target")) with(result) {
                addAll(listOf("-target", "1.6"))
            }
            return result
        } else {
            return emptyList()
        }
    }

    companion object {
        const val TASK_GENERATE_DEX = "generateDex"
    }

    @Task(name = TASK_GENERATE_DEX, description = "Generate the dex file", runBefore = arrayOf("assemble"),
            runAfter = arrayOf("compile"))
    fun taskGenerateDex(project: Project): TaskResult {
        //
        // Call dx to generate classes.dex
        //
        val buildToolsDir = buildToolsVersion(project)
        val dx = "${androidHome(project)}/build-tools/$buildToolsDir/dx" +
                if (OperatingSystem.current().isWindows()) ".bat" else ""
        val classesDexDir = KFiles.joinDir(AndroidFiles.intermediates(project), "dex",
                context.variant.toIntermediateDir())
        File(classesDexDir).mkdirs()
        val classesDex = "classes.dex"
        val outClassesDex = KFiles.joinDir(classesDexDir, classesDex)

        // java.exe -Xmx1024M -Dfile.encoding=windows-1252 -Duser.country=US -Duser.language=en -Duser.variant -cp D:\android\adt-bundle-windows-x86_64-20140321\sdk\build-tools\23.0.1\lib\dx.jar com.android.dx.command.Main --dex --verbose --num-threads=4 --output C:\Users\cbeust\android\android_hello_world\app\build\intermediates\dex\pro\debug C:\Users\cbeust\android\android_hello_world\app\build\intermediates\classes\pro\debug

        val javaExecutable = JavaInfo.create(File(SystemProperties.javaBase)).javaExecutable!!
        RunCommand(javaExecutable.absolutePath).run(listOf(
                "-cp", KFiles.joinDir(androidHome(project), "build-tools", buildToolsVersion(project), "lib", "dx.jar"),
                "com.android.dx.command.Main",
                "--dex", "--verbose", "--num-threads=4",
                "--output", outClassesDex,
                   //KFiles.joinDir(intermediates(project), "dex", context.variant.toIntermediateDir()),
                project.classesDir(context)
        ))
//        val args = listOf("--dex", "--output", outClassesDex)
//        val otherArgs =
//            project.dependencies?.let {
//                it.dependencies.map {
//                    it.jarFile.get().path
//                }.filter { ! it.endsWith(".aar") && ! it.endsWith("android.jar") }
//            } ?: emptyList()
//        RunCommand(dx).run(args + otherArgs)

        //
        // Add classes.dex to existing .ap_
        // Because aapt doesn't handle directory moving, we need to cd to classes.dex's directory so
        // that classes.dex ends up in the root directory of the .ap_.
        //
        AaptCommand(project, aapt(project), "add").apply {
            directory = File(outClassesDex).parentFile
        }.call(listOf("-v", KFiles.joinDir(
                File(temporaryApk(project, context.variant.shortArchiveName)).absolutePath), classesDex))

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
    fun taskSignApk(project: Project): TaskResult {
        val apk = apk(project, context.variant.shortArchiveName)
        val temporaryApk = temporaryApk(project, context.variant.shortArchiveName)
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

    @Task(name = "install", description = "Install the apk file", runAfter = arrayOf(TASK_GENERATE_DEX),
            runBefore = arrayOf("assemble"))
    fun taskInstall(project: Project): TaskResult {
        val apk = apk(project, context.variant.shortArchiveName)
        RunCommand(adb(project)).useErrorStreamAsErrorIndicator(false).run(args = listOf(
                "install", "-r",
                apk))
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

    // IBuildDirectoryInterceptor
    override fun intercept(project: Project, context: KobaltContext, buildDirectory: String): String {
        val result = KFiles.joinDir(AndroidFiles.intermediates(project), "classes",
                context.variant.toIntermediateDir())
        return result
    }

    // ISourceDirectoriesInterceptor
    override fun intercept(project: Project, context: KobaltContext, sourceDirectories: List<File>): List<File> {
        return sourceDirectories.map { File("app", it.path)}
    }

    // ICompilerInterceptor
    override fun intercept(project: Project, context: KobaltContext, actionInfo: CompilerActionInfo)
            : CompilerActionInfo {
        val newOutputDir = KFiles.joinDir("kobaltBuild", "intermediates", "classes",
                context.variant.toIntermediateDir())
        return actionInfo.copy(outputDir = File(newOutputDir))
    }


}
