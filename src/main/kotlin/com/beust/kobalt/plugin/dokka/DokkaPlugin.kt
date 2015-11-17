package com.beust.kobalt.plugin.dokka

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.ConfigPlugin
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.packaging.PackagingPlugin
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaLogger
import org.jetbrains.dokka.SourceLinkDefinition
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DokkaPlugin @Inject constructor(val depFactory: DepFactory) : ConfigPlugin<DokkaConfig>() {
    override val name = PLUGIN_NAME

    companion object {
        const val PLUGIN_NAME = "dokka"
    }

    /**
     * Probably no point in running this task if "assemble" hasn't completed.
     */
    @Task(name = "dokka", description = "Run dokka", runAfter = arrayOf(PackagingPlugin.TASK_ASSEMBLE))
    fun taskDokka(project: Project) : TaskResult {
        val config = configurationFor(project)
        val classpath = context.dependencyManager.calculateDependencies(project, context)
        val buildDir = project.buildDirectory!!
        val classpathList = (classpath.map { it.jarFile.get().absolutePath } + listOf(buildDir))
        var success = true
        if (config != null) {
            if (! config.skip) {
                val outputDir = buildDir + "/" +
                        if (config.outputDir.isBlank()) "doc" else config.outputDir

                val gen = DokkaGenerator(
                        KobaltDokkaLogger { success = false },
                        classpathList,
                        project.sourceDirectories.toList(),
                        config.samplesDirs,
                        config.includeDirs,
                        config.moduleName,
                        outputDir,
                        config.outputFormat,
                        config.sourceLinks.map { SourceLinkDefinition(it.dir, it.url, it.urlSuffix) }
                )
                gen.generate()
                log(2, "Documentation generated in $outputDir")
            } else {
                log(2, "skip is true, not generating the documentation")
            }
        } else {
            log(2, "No dokka configuration found for project ${project.name}, skipping it")
        }
        return TaskResult(success)
    }
}

class KobaltDokkaLogger(val onErrorCallback: () -> Unit = {}) : DokkaLogger {
    override fun error(message: String) {
        KobaltLogger.logger.error("Dokka", message)
        onErrorCallback()
    }

    override fun info(message: String) {
        KobaltLogger.logger.log(2, message)
    }

    override fun warn(message: String) {
        KobaltLogger.logger.warn("Dokka", message)
    }
}

class SourceLinkMapItem {
    var dir: String = ""
    var url: String = ""
    var urlSuffix: String? = null
}

class DokkaConfig(
        var samplesDirs: List<String> = emptyList(),
        var includeDirs: List<String> = emptyList(),
        var outputDir: String = "",
        var outputFormat: String = "html",
        var sourceLinks : ArrayList<SourceLinkMapItem> = arrayListOf<SourceLinkMapItem>(),
        var moduleName: String = "",
        var skip: Boolean = false) {

    fun sourceLinks(init: SourceLinkMapItem.() -> Unit) {
        val s: SourceLinkMapItem = SourceLinkMapItem().apply { init() }
        sourceLinks.add(s)
    }
}

@Directive
public fun Project.dokka(init: DokkaConfig.() -> Unit) = let { project ->
    with(DokkaConfig()) {
        init()
        (Kobalt.findPlugin(DokkaPlugin.PLUGIN_NAME) as DokkaPlugin).addConfiguration(project, this)
    }
}

