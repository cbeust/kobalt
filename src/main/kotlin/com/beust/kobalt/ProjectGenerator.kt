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
                map.put("name", currentDir.name)
                map.put("group", "com.example")
                map.put("version", "0.1")
                map.put("directory", currentDir.absolutePath)
                map.put("sourceDirectories", it.defaultSourceDirectories)
                map.put("sourceDirectoriesTest", it.defaultTestDirectories)
                map.put("imports", "import com.beust.kobalt.plugin.${it.name}.*")
                map.put("directive", it.name + "Project")
            }
        }

        var mainDeps = arrayListOf<Dependency>()
        var testDeps = arrayListOf<Dependency>()
        map.put("mainDependencies", mainDeps)
        map.put("testDependencies", testDeps)
        if(File("pom.xml").exists()) {
            importPom(mainDeps, map, testDeps)
        }

        val fileInputStream = javaClass.classLoader.getResource("build-template.mustache").openStream()
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        var mf = DefaultMustacheFactory();
        var mustache = mf.compile(InputStreamReader(fileInputStream), "kobalt");
        mustache.execute(pw, map).flush();
        KFiles.saveFile(File(args.buildFile), sw.toString())
    }

    private fun importPom(mainDeps: ArrayList<Dependency>, map: HashMap<String, Any?>, testDeps: ArrayList<Dependency>) {
        var pom = Pom("imported", File("pom.xml"))
        map.put("group", pom.groupId ?: "com.example")
        map.put("artifactId", pom.artifactId ?: "com.example")
        map.put("version", pom.version ?: "0.1")
        map.put("name", pom.name ?: pom.artifactId)
        val partition = pom.dependencies.groupBy { it.scope }
              //                  .filter { it.key == null }
              .flatMap { it.value }
              .sortedBy { it.groupId + ":" + it.artifactId }
              .partition { it.scope != "test" }
        mainDeps.addAll(partition.first)
        testDeps.addAll(partition.second)
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
