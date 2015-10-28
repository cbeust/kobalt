package com.beust.kobalt.plugin.android

import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.FileDependency
import com.beust.kobalt.misc.RunCommand
import com.beust.kobalt.misc.log
import java.io.File
import java.nio.file.Paths
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

class AndroidConfiguration() {
}

@Directive
public fun android(init: AndroidConfiguration.() -> Unit) : AndroidConfiguration {
    val pd = AndroidConfiguration()
    pd.init()
    return pd
}

@Singleton
public class AndroidPlugin @Inject constructor() : BasePlugin() {
    val ANDROID_HOME = "/Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk"
    override val name = "android"

    val compileSdkVersion = "23"
    val buildToolsVersion = "23.0.1"

    override fun apply(project: Project, context: KobaltContext) {
        log(1, "Applying plug-in Android on project $project")
        project.compileDependencies.add(FileDependency(androidJar.toString()))
    }

    fun dirGet(dir: Path, vararg others: String) : String {
        val result = Paths.get(dir.toString(), *others)
        with(result.toFile()) {
            deleteRecursively()
            mkdirs()
        }
        return result.toString()
    }

    val androidJar = Paths.get(ANDROID_HOME, "platforms", "android-$compileSdkVersion", "android.jar")

    @Task(name = "generateR", description = "Generate the R.java file", runBefore = arrayOf("compile"))
    fun taskGenerateRFile(project: Project) : TaskResult {

        val flavor = "debug"
        val androidJar = Paths.get(ANDROID_HOME, "platforms", "android-$compileSdkVersion", "android.jar")
        val applicationId = "com.beust.example"
        val intermediates = Paths.get(project.directory, "app", "build", "intermediates")
        val manifestDir = Paths.get(project.directory, "app", "src", "main").toString()
        val manifestIntermediateDir = dirGet(intermediates, "manifests", "full", flavor)
        val manifest = Paths.get(manifestDir, "AndroidManifest.xml")
        val generated = Paths.get(project.directory, "app", "build", "generated")
        val aapt = "$ANDROID_HOME/build-tools/$buildToolsVersion/aapt"
        val outputDir = dirGet(intermediates, "resources", "resources-$flavor")

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
        return TaskResult()
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
