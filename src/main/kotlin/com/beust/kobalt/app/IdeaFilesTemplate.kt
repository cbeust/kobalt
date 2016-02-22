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
        generateModulesXml(data, outputDir)
        println("Generating ideaFiles")
    }

    private fun generateModulesXml(data: DependencyData.GetDependenciesData, outputDir: File) {
        val modulesXmlFile = File(outputDir, "modules.xml")
        with(arrayListOf<String>()) {
            add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            add("<project version=\"4\">")
            add("  <component name=\"ProjectModuleManager\">")
            add("    <modules>")

            fun moduleLine(iml: String)
                = "      <module fileurl=\"file://\$PROJECT_DIR$/$iml\"" +
                    " filepath=\"\$PROJECT_DIR$/$iml\" />"

            add(moduleLine("kobalt/Build.kt.iml"))
            data.projects.forEach {
                val iml = KFiles.joinDir(it.directory, it.name + ".iml")
                add(moduleLine(iml))
            }

            add("    </modules>")
            add("  </component>")
            add("</project>")
            modulesXmlFile.writeText(joinToString("\n"))
            log(1, "Created $modulesXmlFile")
        }
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
            log(1, "Created $file")
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
