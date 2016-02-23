package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.Constants
import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.remote.DependencyData
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

    override val instructions = "IDEA files generated"

    companion object {
        val IDEA_DIR = ".idea"
    }

    override fun generateTemplate(args: Args, classLoader: ClassLoader) {
        val dependencyData = Kobalt.INJECTOR.getInstance(DependencyData::class.java)
        val data = dependencyData.dependenciesDataFor(Constants.BUILD_FILE_PATH, args)
        val outputDir = KFiles.makeDir(".")//KFiles.makeDir(homeDir("t/idea"))
        generateLibraries(data, outputDir)
        generateModulesXml(data, outputDir)
        generateImlFiles(data, outputDir)
    }

    private fun generateImlFiles(data: DependencyData.GetDependenciesData, outputDir: File) {
        data.projects.forEach { project ->
            KFiles.makeDir(outputDir.absolutePath, File(imlName(project)).parent)
            val file = File(KFiles.joinDir(outputDir.path, imlName(project)))
            with(arrayListOf<String>()) {
                add("""<?xml version="1.0" encoding="UTF-8"?>""")
                add("""<module type="JAVA_MODULE" version="4">""")
                add("""  <component name="NewModuleRootManager" inherit-compiler-output="true">""")

                add("""<exclude-output />""")

                add("  <content url=\"file://\$MODULE_DIR$\">")

                //
                // Source directories
                //
                fun sourceDir(dir: String, isTest: Boolean)
                    = "    <sourceFolder url=\"file://\$MODULE_DIR$/$dir\" isTestSource=\"$isTest\" />"

                project.sourceDirs.forEach { sourceDir ->
                    add(sourceDir(sourceDir, false))
                }
                project.testDirs.forEach { sourceDir ->
                    add(sourceDir(sourceDir, true))
                }

                add("""  </content>""")

                //
                // Libraries
                //
                add("""   <orderEntry type="inheritedJdk" />""")

                (project.name + compileSuffix()).let {
                    add("  <orderEntry type=\"library\" name=\"$it\" level=\"project\" />")
                }
                (project.name + testSuffix()).let {
                    add("  <orderEntry type=\"library\" scope=\"TEST\" name=\"$it\" level=\"project\" />")
                }

                //
                // Dependent projects
                //
                project.dependentProjects.forEach { dp ->
                    add("""  <orderEntry type="module" module-name="$dp" />""")
                }

                add("  </component>")
                add("</module>")

                writeFile(this, file)

            }
        }
    }

    private fun imlName(project: DependencyData.ProjectData) =
            KFiles.joinDir(project.directory, project.name + ".iml")

    private fun generateModulesXml(data: DependencyData.GetDependenciesData, outputDir: File) {
        val modulesXmlFile = File(KFiles.joinDir(IDEA_DIR, outputDir.path, "modules.xml"))
        with(arrayListOf<String>()) {
            add("""<?xml version="1.0" encoding="UTF-8"?>""")
            add("""<project version="4">""")
            add("""  <component name="ProjectModuleManager">""")
            add("    <modules>")

            fun moduleLine(iml: String)
                = "      <module fileurl=\"file://\$PROJECT_DIR$/$iml\"" +
                    " filepath=\"\$PROJECT_DIR$/$iml\" />"

            add(moduleLine("kobalt/Build.kt.iml"))
            data.projects.forEach {
                add(moduleLine(imlName(it)))
            }

            add("    </modules>")
            add("  </component>")
            add("</project>")
            writeFile(this, modulesXmlFile)
        }
    }

    private fun compileSuffix() = " (Compile)"
    private fun testSuffix() = " (Test)"

    private fun generateLibraries(data: DependencyData.GetDependenciesData, outputDir: File) {
        data.projects.forEach {
            generateLibrary(it.name, it.compileDependencies, compileSuffix(), outputDir)
            generateLibrary(it.name, it.testDependencies, testSuffix(), outputDir)
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
            add("""<component name="libraryTable">""")
            add("""  <library name="$libraryName">""")
            addAll(generateList(compileDependencies, "CLASSES"))
            addAll(generateList(emptyList(), "JAVADOC"))
            addAll(generateList(emptyList(), "SOURCES"))
            add("  </library>")
            add("</component>")
            val fileName = libraryName.replace(" ", "_").replace("-", "_").replace("(", "_").replace(")", "_")
                .replace(".", "_")
            writeFile(this, File(KFiles.joinDir(IDEA_DIR, librariesOutputDir.path, fileName + ".xml")))
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

    private fun writeFile(lines: List<String>, file: File) {
        file.writeText(lines.joinToString("\n"))
        log(2, "Created ${file.absolutePath}")
    }
}
