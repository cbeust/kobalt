package com.beust.kobalt

import com.beust.kobalt.api.ICompilerInfo
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.Pom
import com.beust.kobalt.maven.Pom.Dependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltLogger
import com.github.mustachejava.DefaultMustacheFactory
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

/**
 * Generate a new project.
 */
public class ProjectGenerator : KobaltLogger {
    fun run(args: Args) {
        if (File(args.buildFile).exists()) {
            log(1, "Build file ${args.buildFile} already exists, not overwriting it")
            return
        }

        val compilerInfos = detect(File("."))
        if (compilerInfos.size() > 1) {
            log(1, "Multi language project detected, not supported yet")
        }
        val map = hashMapOf<String, Any?>()
        map.put("directive", if (compilerInfos.isEmpty()) "project" else compilerInfos.get(0).directive)
        if (compilerInfos.size() > 0) {
            compilerInfos.get(0).let {
                val currentDir = File(".").absoluteFile.parentFile
                with(map) {
                    put("name", currentDir.name)
                    put("group", "com.example")
                    put("version", "0.1")
                    put("directory", currentDir.absolutePath)
                    put("sourceDirectories", it.defaultSourceDirectories)
                    put("sourceDirectoriesTest", it.defaultTestDirectories)
                    put("imports", "import com.beust.kobalt.plugin.${it.name}.*")
                    put("directive", it.name + "Project")
                }
            }
        }

        var mainDeps = arrayListOf<Dependency>()
        var testDeps = arrayListOf<Dependency>()
        map.put("mainDependencies", mainDeps)
        map.put("testDependencies", testDeps)
        File("pom.xml").let {
            if (it.exists()) {
                importPom(it, mainDeps, testDeps, map)
            }
        }

        val fileInputStream = javaClass.classLoader.getResource("build-template.mustache").openStream()
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        var mf = DefaultMustacheFactory();
        var mustache = mf.compile(InputStreamReader(fileInputStream), "kobalt");
        mustache.execute(pw, map).flush();
        KFiles.saveFile(File(args.buildFile), sw.toString())
    }

    private fun importPom(pomFile: File, mainDeps: ArrayList<Dependency>, testDeps: ArrayList<Dependency>,
            map: HashMap<String, Any?>) {
        var pom = Pom("imported", pomFile)
        with(map) {
            put("group", pom.groupId ?: "com.example")
            put("artifactId", pom.artifactId ?: "com.example")
            put("version", pom.version ?: "0.1")
            put("name", pom.name ?: pom.artifactId)
        }

        val properties = pom.properties
        val mapped = properties.entrySet().toMap({it.key}, {translate(it.key)})

        map.put("properties", properties
              .entrySet()
              .map({ Pair(mapped.get(it.key), it.value) }))
        
        val partition = pom.dependencies.groupBy { it.scope }
              .flatMap { it.value }
              .map { updateVersion(it, mapped) }
              .sortedBy { it.groupId + ":" + it.artifactId }
              .partition { it.scope != "test" }

        mainDeps.addAll(partition.first)
        testDeps.addAll(partition.second)
    }

    private fun updateVersion(dep: Dependency, mapped: Map<String, String>): Dependency {
        if ( dep.version.startsWith("\${")) {
            val property = dep.version.substring(2, dep.version.length() - 1)
            return Dependency(dep.groupId, dep.artifactId, "\${${mapped.get(property)}}", dep.optional, dep.scope)
        } else {
            return dep
        }
    }

    private fun translate(key: String) : String {
        val split = key.split('.')
        return split.mapIndexed( { index, value -> if (index == 0) value else value.upperFirst() }).join("")
    }

    private fun String.upperFirst(): String {
        if (this.isBlank()) {
            return this
        } else {
            return this.substring(0, 1).toUpperCase() + this.substring(1)
        }
    }

    /**
     * Detect all the languages contained in this project.
     */
    private fun detect(dir: File) : List<ICompilerInfo> {
        val result = arrayListOf<Pair<ICompilerInfo, List<File>>>()
        Kobalt.compilers.forEach {
            val managedFiles = it.findManagedFiles(dir)
            if (managedFiles.size() > 0) {
                result.add(Pair(it, managedFiles))
            }
        }
        Collections.sort(result, { p1, p2 -> p1.second.size().compareTo(p2.second.size()) })
        return result.map { it.first }
    }
}
