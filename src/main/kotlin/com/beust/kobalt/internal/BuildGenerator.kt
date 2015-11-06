package com.beust.kobalt.internal

import com.beust.kobalt.ProjectGenerator
import com.beust.kobalt.api.IInitContributor
import com.beust.kobalt.maven.Pom
import com.beust.kobalt.misc.KFiles
import com.github.mustachejava.DefaultMustacheFactory
import java.io.*
import java.util.*

/**
 * Abstract base class for the build generators that use build-template.mustache.
 */
abstract class BuildGenerator : IInitContributor {
    abstract val defaultSourceDirectories : ArrayList<String>
    abstract val defaultTestDirectories : ArrayList<String>
    abstract val directive : String
    abstract val name : String
    abstract val fileMatch : (String) -> Boolean

    override fun generateBuildFile(os: OutputStream) {
        PrintWriter(os).use {
            it.print(buildFileContent)
        }
    }

    override fun filesManaged(dir: File) = KFiles.findRecursively(dir, fileMatch).size

    private fun importPom(pomFile: File, mainDeps: ArrayList<Pom.Dependency>, testDeps: ArrayList<Pom.Dependency>,
            map: HashMap<String, Any?>) {
        var pom = Pom("imported", pomFile.absoluteFile)
        with(map) {
            put("group", pom.groupId ?: "com.example")
            put("artifactId", pom.artifactId ?: "com.example")
            put("version", pom.version ?: "0.1")
            put("name", pom.name ?: pom.artifactId)
            put("repositories", pom.repositories.map({ "\"$it\"" }).joinToString(","))
        }

        val properties = pom.properties
        val mapped = properties.entries.toMap({it.key}, { ProjectGenerator.toIdentifier(it.key) })

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
            val map = hashMapOf<String, Any?>()
            map.put("directive", directive)
            val currentDir = File(".").absoluteFile.parentFile
            with(map) {
                put("name", currentDir.name)
                put("group", "com.example")
                put("version", "0.1")
                put("directory", currentDir.absolutePath)
                put("sourceDirectories", defaultSourceDirectories)
                put("sourceDirectoriesTest", defaultTestDirectories)
                put("imports", "import com.beust.kobalt.plugin.$name.*")
                put("directive", name + "Project")
            }

            var mainDeps = arrayListOf<Pom.Dependency>()
            var testDeps = arrayListOf<Pom.Dependency>()
            map.put("mainDependencies", mainDeps)
            map.put("testDependencies", testDeps)
            File("pom.xml").let {
                if (it.absoluteFile.exists()) {
                    importPom(it, mainDeps, testDeps, map)
                }
            }

            val fileInputStream = javaClass.classLoader.getResource("build-template.mustache").openStream()
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            var mf = DefaultMustacheFactory();
            var mustache = mf.compile(InputStreamReader(fileInputStream), "kobalt");
            mustache.execute(pw, map).flush();
            return sw.toString()
        }
}

