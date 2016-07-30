package com.beust.kobalt

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.KobaltPluginXml
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.maven.aether.KobaltAether
import org.testng.annotations.BeforeClass
import java.io.File
import java.nio.file.Paths

open class BaseTest {
    val aether : KobaltAether get() = Kobalt.INJECTOR.getInstance(KobaltAether::class.java)
    val taskManager : TaskManager get() = Kobalt.INJECTOR.getInstance(TaskManager::class.java)
    val context = KobaltContext(Args())

    @BeforeClass
    fun bc() {
        Kobalt.init(TestModule())
        context.aether = aether
    }

    fun compileBuildFile(buildFileText: String, args: Args, compilerFactory: BuildFileCompiler.IFactory)
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
}