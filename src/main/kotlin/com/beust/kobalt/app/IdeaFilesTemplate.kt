package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.Constants
import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.api.ITemplateContributor
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.remote.DependencyData
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.KobaltPlugin
import com.google.inject.Inject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths

/**
 * A template that generates IDEA files.
 */
class IdeaFilesTemplate @Inject constructor() : ITemplate {
    override val templateName = "idea"
    override val templateDescription = "Generate files required by IDEA to build the project"
    override val pluginName = KobaltPlugin.PLUGIN_NAME

    override val instructions = "IDEA files generated"

    companion object {
        val IDEA_DIR = File(".idea").apply { mkdirs() }
    }

    override fun generateTemplate(args: Args, classLoader: ClassLoader) {
        val dependencyData = Kobalt.INJECTOR.getInstance(DependencyData::class.java)
        val data = dependencyData.dependenciesDataFor(Constants.BUILD_FILE_PATH, args)
        val outputDir = File(".")// KFiles.makeDir(".")//KFiles.makeDir(homeDir("t/idea"))
        generateLibraries(data, outputDir)
        generateModulesXml(data, outputDir)
        generateImlFiles(classLoader, data, outputDir)
        generateMiscXml(classLoader, outputDir)
    }

    private fun templatePath(fileName: String) =
            KFiles.joinDir(ITemplateContributor.DIRECTORY_NAME, templateName, fileName)

    private fun writeTemplate(classLoader: ClassLoader, outputDir: File, fileName: String) {
        log(2, "Opening template " + templatePath(fileName))
        val ins = classLoader.getResource(templatePath(fileName)).openConnection().inputStream
        val outputFile = File(KFiles.joinDir(outputDir.absolutePath, fileName))
        outputFile.parentFile.mkdir()
        FileOutputStream(outputFile).use { fos ->
            KFiles.copy(ins, fos)
        }
        log(2, "Created " + outputFile.path)
    }

    private fun generateMiscXml(classLoader: ClassLoader, outputDir: File)
        = writeTemplate(classLoader, Paths.get(outputDir.path, IDEA_DIR.path).normalize().toFile(), "misc.xml")

    private fun generateImlFiles(classLoader: ClassLoader, data: DependencyData.GetDependenciesData, outputDir: File) {
        //
        // Build.kt.iml
        //
        writeTemplate(classLoader, File(KFiles.joinDir(outputDir.absolutePath, "kobalt")), "Build.kt.iml")

        //
        // iml files for each individual project
        //
        data.projects.forEach { project ->
            val file = File(outputDir.absolutePath, imlName(project))
            file.parentFile.mkdirs()
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

                (project.name + COMPILE_SUFFIX).let {
                    add("  <orderEntry type=\"library\" name=\"$it\" level=\"project\" />")
                }
                (project.name + TEST_SUFFIX).let {
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
            Paths.get(KFiles.joinDir(project.directory, project.name + ".iml")).normalize().toString()

    private fun generateModulesXml(data: DependencyData.GetDependenciesData, outputDir: File) {
        val modulesXmlFile = File(KFiles.joinDir(IDEA_DIR.path, outputDir.path, "modules.xml"))
        with(arrayListOf<String>()) {
            add("""<?xml version="1.0" encoding="UTF-8"?>""")
            add("""<project version="4">""")
            add("""  <component name="ProjectModuleManager">""")
            add("    <modules>")

            fun moduleLine(iml: String)
                = "      <module fileurl=\"file://\$PROJECT_DIR$/$iml\" filepath=\"\$PROJECT_DIR$/$iml\" />"

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

    private val COMPILE_SUFFIX = " (Compile)"
    private val TEST_SUFFIX = " (Test)"

    private fun generateLibraries(data: DependencyData.GetDependenciesData, outputDir: File) {
        data.projects.forEach {
            generateLibrary(it.name, it.compileDependencies, COMPILE_SUFFIX, outputDir)
            generateLibrary(it.name, it.testDependencies, TEST_SUFFIX, outputDir)
        }
        val kobaltDd = DependencyData.DependencyData("kobalt", "compile",
                KFiles.joinDir(KFiles.distributionsDir, Kobalt.version, "kobalt", "wrapper",
                        "kobalt-${Kobalt.version}.jar"))
        generateLibrary("kobalt.jar", listOf(kobaltDd), "", outputDir)
    }

    private fun generateLibrary(name: String, compileDependencies: List<DependencyData.DependencyData>,
            suffix: String, outputDir: File) {
        val libraryName = name + suffix
        val librariesOutputDir = KFiles.joinAndMakeDir(IDEA_DIR.path, outputDir.path, "libraries")
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
            writeFile(this, File(librariesOutputDir, fileName + ".xml"))
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
