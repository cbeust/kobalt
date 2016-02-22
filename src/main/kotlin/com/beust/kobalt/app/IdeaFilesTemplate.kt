package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.remote.DependencyData
import com.beust.kobalt.homeDir
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.KobaltPlugin
import com.google.inject.Inject
import java.io.File

/**
 * A template that generates IDEA files.
 */
class IdeaFilesTemplate @Inject constructor() : ITemplate {
    override val templateName = "idea"
    override val templateDescription = "Generate files required by IDEA to build the project"
    override val pluginName = KobaltPlugin.PLUGIN_NAME

    override fun generateTemplate(args: Args, classLoader: ClassLoader) {
        val dependencyData = Kobalt.INJECTOR.getInstance(DependencyData::class.java)
        val data = dependencyData.dependenciesDataFor(homeDir("kotlin/kobalt/kobalt/src/Build.kt"), args)
        val outputDir = KFiles.makeDir(homeDir("t/idea"))
        generateLibraries(data, outputDir)
        println("Generating ideaFiles")
    }

    private fun generateLibraries(data: DependencyData.GetDependenciesData, outputDir: File) {
        data.projects.forEach {
            generateLibrary(it.name, it.compileDependencies, " (Compile)", outputDir)
            generateLibrary(it.name, it.testDependencies, " (Test)", outputDir)
        }
        val kobaltDd = DependencyData.DependencyData("kobalt", "compile",
                KFiles.joinDir(KFiles.distributionsDir, Kobalt.version, "kobalt", "wrapper",
                        "kobalt-${Kobalt.version}.jar"))
        generateLibrary("kobalt.jar", listOf(kobaltDd), "", outputDir)
    }

    private fun generateLibrary(name: String, compileDependencies: List<DependencyData.DependencyData>,
            suffix: String, outputDir: File) {
        val libraryName = name + suffix
        log(1, "Generating libraries for $name$suffix")
        val librariesOutputDir = KFiles.makeDir(outputDir.path, "libraries")
        with(arrayListOf<String>()) {
            add("<component name=\"libraryTable\">")
            add("  <library name=\"$libraryName\">")
            addAll(generateList(compileDependencies, "CLASSES"))
            addAll(generateList(emptyList(), "JAVADOC"))
            addAll(generateList(emptyList(), "SOURCES"))
            add("  </library>")
            add("</component>")
            val fileName = libraryName.replace(" ", "_").replace("-", "_").replace("(", "_").replace(")", "_")
                .replace(".", "_")
            val file = File(librariesOutputDir, fileName + ".xml")
            file.writeText(joinToString("\n"))
        }
    }

    private fun generateList(libraries: List<DependencyData.DependencyData>, tag: String) : List<String> {
        if (libraries.isEmpty()){
            return listOf("    <$tag />")
        } else {
            val result = arrayListOf<String>()
            result.add("    <$tag>")
            libraries.forEach {
                val path =
                        if (it.path.contains(".kobalt")) {
                            val ind = it.path.indexOf(".kobalt")
                            "\$USER_HOME$/" + it.path.substring(ind)
                        } else {
                            it.path
                        }
                result.add("      <root url=\"jar://$path!/\" />")
            }

            result.add("    </$tag>")

            return result
        }
    }

}
