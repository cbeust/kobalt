package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.api.ITemplateContributor
import com.beust.kobalt.maven.Pom
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.KobaltPlugin
import com.github.mustachejava.DefaultMustacheFactory
import java.io.*
import java.util.*

/**
 * Abstract base class for the "java" and "kotlin" templates.
 */
abstract class LanguageTemplateGenerator : ITemplate {
    override val pluginName = KobaltPlugin.PLUGIN_NAME

    abstract val defaultSourceDirectories : HashSet<String>
    abstract val defaultTestDirectories : HashSet<String>
    abstract val mainDependencies : ArrayList<Pom.Dependency>
    abstract val testDependencies : ArrayList<Pom.Dependency>
    abstract val directive : String
    abstract val fileMatch : (String) -> Boolean
    abstract val fileMap: List<FileInfo>
    abstract val mainClass : String

    class FileInfo(val dir: String, val fileName: String, val mustacheFileName: String)

    private fun generateAdditionalFiles(args: Args, classLoader: ClassLoader) {
        val map = mapOf("packageName" to PACKAGE_NAME)

        fileMap.forEach {
            val mustache = it.mustacheFileName
            val fileInputStream = javaClass.classLoader
                    .getResource(ITemplateContributor.DIRECTORY_NAME + "/$templateName/$mustache").openStream()
            val createdFile = File(KFiles.joinDir(it.dir, it.fileName))
            Mustache.generateFile(fileInputStream, File(KFiles.joinDir(it.dir, it.fileName)), map)
            kobaltLog(2, "Created $createdFile")
        }
    }

    private fun maybeGenerateAdditionalFiles(args: Args, classLoader: ClassLoader) {
        val existingFiles =
            if (File("src").exists()) {
                KFiles.findRecursively(File("src"), fileMatch).size > 0
            } else {
                false
            }

        if (! existingFiles) {
            generateAdditionalFiles(args, classLoader)
        }
    }

    companion object {
        val PACKAGE_NAME = "com.example"

        /**
         * Turns a dot property into a proper Kotlin identifier, e.g. common.version -> commonVersion
         */
        fun toIdentifier(key: String): String {
            fun upperFirst(s: String) = if (s.isBlank()) s else s.substring(0, 1).toUpperCase() + s.substring(1)

            return key.split('.').mapIndexed( { index, value -> if (index == 0) value else upperFirst(value) })
                    .joinToString("")
        }
    }

    override fun generateTemplate(args: Args, classLoader: ClassLoader) {
        generateBuildFile(args, classLoader)
        maybeGenerateAdditionalFiles(args, classLoader)
    }

    private fun generateBuildFile(args: Args, classLoader: ClassLoader) {
        val file = File(args.buildFile)
        if (! file.exists()) {
            PrintWriter(FileOutputStream(file)).use {
                it.print(buildFileContent)
            }
        } else {
            kobaltLog(1, "Build file already exists, not overwriting it")
        }
    }

    private fun importPom(pomFile: File, mainDeps: ArrayList<Pom.Dependency>, testDeps: ArrayList<Pom.Dependency>,
            map: HashMap<String, Any?>) {
        val pom = Pom("imported", pomFile.absoluteFile)
        with(map) {
            put("group", pom.groupId ?: PACKAGE_NAME)
            put("artifactId", pom.artifactId ?: PACKAGE_NAME)
            put("version", pom.version ?: "0.1")
            put("name", pom.name ?: pom.artifactId)
            put("repositories", pom.repositories.map({ "\"$it\"" }).joinToString(","))
        }

        val properties = pom.properties
        val mapped = properties.entries.associateBy({ it.key }, { toIdentifier(it.key) })

        map.put("properties", properties.entries.map({ Pair(mapped[it.key], it.value) }))

        val partition = pom.dependencies.groupBy { it.scope }
                .flatMap { it.value }
                .map { updateVersion(it, mapped) }
                .sortedBy { it.groupId + ":" + it.artifactId }
                .partition { it.scope != "test" }

        mainDeps.addAll(partition.first)
        testDeps.addAll(partition.second)
    }

    private fun updateVersion(dep: Pom.Dependency, mapped: Map<String, String>) =
            if ( dep.version.startsWith("\${")) {
                val property = dep.version.substring(2, dep.version.length - 1)
                Pom.Dependency(dep.groupId, dep.artifactId, dep.packaging, "\${${mapped[property]}}", dep.optional,
                        dep.scope)
            } else {
                dep
            }

    private val buildFileContent: String
        get() {
            val currentDir = File(".").absoluteFile.parentFile
            val map = hashMapOf<String, Any?>()
            with(map) {
                put("mainClass", mainClass)
                put("directive", directive)
                put("name", currentDir.name)
                put("group", PACKAGE_NAME)
                put("version", "0.1")
                put("directory", currentDir.absolutePath)
                put("sourceDirectories", defaultSourceDirectories)
                put("sourceDirectoriesTest", defaultTestDirectories)
                put("mainDependencies", mainDependencies)
                put("testDependencies", testDependencies)
                put("imports", "import com.beust.kobalt.plugin.$templateName.*")
                put("directive", "project")
            }

            File("pom.xml").let {
                if (it.absoluteFile.exists()) {
                    importPom(it, mainDependencies, testDependencies, map)
                }
            }


            val fileInputStream = javaClass.classLoader
                    .getResource(ITemplateContributor.DIRECTORY_NAME + "/build.mustache").openStream()
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            var mf = DefaultMustacheFactory()
            var mustache = mf.compile(InputStreamReader(fileInputStream), "kobalt")
            mustache.execute(pw, map).flush()
            return sw.toString()
        }
}

