package com.beust.kobalt.app.java

import com.beust.kobalt.Args
import com.beust.kobalt.api.ITemplateContributor
import com.beust.kobalt.app.BuildGenerator
import com.beust.kobalt.internal.Mustache
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File

class JavaBuildGenerator: BuildGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/java")
    override val defaultTestDirectories = hashSetOf("src/test/java")
    override val directive = "project"
    override val templateName = "java"
    override val templateDescription = "Generate a simple Java project"
    override val fileMatch = { f: String -> f.endsWith(".java") }
    override val instructions = "Now you can run either `./kobaltw test` or `./kobaltw run`"

    override fun generateAdditionalFiles(args: Args, classLoader: ClassLoader) {
        println("Generating Java files")

        class FileInfo(val dir: String, val fileName: String, val mustacheFileName: String)

        val fileMap = listOf(
                FileInfo("src/main/java/" + PACKAGE_NAME.replace(".", "/"), "Example.java", "java.mustache"),
                FileInfo("src/test/java/" + PACKAGE_NAME.replace(".", "/"), "ExampleTest.java", "java-test.mustache")
        )

        val map = mapOf("packageName" to PACKAGE_NAME)

        fileMap.forEach {
            val mustache = it.mustacheFileName
            val fileInputStream = javaClass.classLoader
                    .getResource(ITemplateContributor.DIRECTORY_NAME + "/$templateName/$mustache").openStream()
            val createdFile = File(KFiles.joinDir(it.dir, it.fileName))
            Mustache.generateFile(fileInputStream, File(KFiles.joinDir(it.dir, it.fileName)), map)
            log(2, "Created $createdFile")
        }

    }
}
