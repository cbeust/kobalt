package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.TestModule
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.build.BuildFile
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Paths

@Guice(modules = arrayOf(TestModule::class))
class ProfileTest @Inject constructor(val compilerFactory: BuildFileCompiler.IFactory) {

    private fun compileBuildFile(buildFileText: String, args: Args, compilerFactory: BuildFileCompiler.IFactory)
            : BuildFileCompiler.FindProjectResult {
        val tmpBuildFile = File.createTempFile("kobaltTest", "").apply {
            deleteOnExit()
            writeText(buildFileText)
        }
        val thisBuildFile = BuildFile(Paths.get(tmpBuildFile.absolutePath), "Build.kt")
        args.buildFile = tmpBuildFile.absolutePath
        val jvmCompilerPlugin = Kobalt.findPlugin("JvmCompiler") as JvmCompilerPlugin
        val pluginInfo = PluginInfo(KobaltPluginXml(), null, null).apply {
            projectContributors.add(jvmCompilerPlugin)
        }
        return compilerFactory.create(listOf(thisBuildFile), pluginInfo).compileBuildFiles(args)
    }

    private fun runTestWithProfile(enabled: Boolean) : Project {
        val buildFileString = """
            | import com.beust.kobalt.*
            | import com.beust.kobalt.api.*
            | val profile = false
            | val p = project {
            |     name = if (profile) "profileOn" else "profileOff"
            | }
            """.trimMargin()

        val args = Args()
        if (enabled) args.profiles = "profile"
//        val jvmCompilerPlugin = Kobalt.findPlugin("JvmCompiler") as JvmCompilerPlugin
//        val pluginInfo = PluginInfo(KobaltPluginXml(), null, null).apply {
//            projectContributors.add(jvmCompilerPlugin)
//        }
//        val projects = buildScriptUtil.runBuildScriptJarFile()
        val compileResult = compileBuildFile(buildFileString, args, compilerFactory)
        return compileResult.projects[0]
    }

    @Test
    fun profilesShouldWork() {
        Kobalt.init(TestModule())
        assertThat(runTestWithProfile(true).name).isEqualTo("profileOn")
//        Kobalt.INJECTOR.getInstance(Plugins::class.java).shutdownPlugins()
//        Kobalt.init(TestModule())
//        val success = File(KFiles.KOBALT_DOT_DIR).deleteRecursively()
//        println("DELETING " + File(KFiles.KOBALT_DOT_DIR).absolutePath + ": $success")
//        assertThat(runTestWithProfile(false).name).isEqualTo("profileOff")
    }
}


