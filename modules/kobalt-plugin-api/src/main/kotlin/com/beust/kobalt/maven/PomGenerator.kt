package com.beust.kobalt.maven

import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import com.google.inject.assistedinject.Assisted
import org.apache.maven.model.Developer
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import java.io.File
import java.io.StringWriter
import java.nio.charset.Charset
import javax.inject.Inject

class PomGenerator @Inject constructor(@Assisted val project: Project) {
    interface IFactory {
        fun create(project: Project) : PomGenerator
    }

    /**
     * Generate the POM file and save it.
     */
    fun generateAndSave() {
        val buildDir = KFiles.makeDir(project.directory, project.buildDirectory)
        val outputDir = KFiles.makeDir(buildDir.path, "libs")
        val NO_CLASSIFIER = null
        val mavenId = MavenId.create(project.group!!, project.artifactId!!, project.packaging, NO_CLASSIFIER,
                project.version!!)
        val pomFile = SimpleDep(mavenId).toPomFileName()
        val outputFile = File(outputDir, pomFile)
        outputFile.writeText(generate(), Charset.defaultCharset())
        kobaltLog(1, "  Created $outputFile")
    }

    /**
     * @return the text content of the POM file.
     */
    fun generate() : String {
        requireNotNull(project.version, { "version mandatory on project ${project.name}" })
        requireNotNull(project.group, { "group mandatory on project ${project.name}" })
        requireNotNull(project.artifactId, { "artifactId mandatory on project ${project.name}" })

        val pom = (project.pom ?: Model()).apply {
            // Make sure the pom has reasonable default values
            if (name == null) name = project.name
            if (artifactId == null) artifactId = project.artifactId
            if (groupId == null) groupId = project.group
            if (modelVersion == null) modelVersion = "4.0.0"
            if (version == null) version = project.version
            if (description == null) description = project.description
            if (url == null) url = project.url
            if (developers == null) {
                developers = listOf(Developer().apply {
                    name = SystemProperties.username
                })
            }
        }

        //
        // Dependencies
        //
        pom.dependencies = arrayListOf<org.apache.maven.model.Dependency>()

        // Compile dependencies
        project.compileDependencies.forEach { dep ->
            pom.dependencies.add(dep.toMavenDependencies())
        }

        // Optional compile dependencies
        project.optionalDependencies.forEach { dep ->
            pom.dependencies.add(dep.toMavenDependencies())
        }

        // Project dependencies
        project.dependsOn.forEach {
            pom.dependencies.add(org.apache.maven.model.Dependency().apply {
                version = it.version
                groupId = it.group
                artifactId = it.artifactId
            })
        }

        val s = StringWriter()
        MavenXpp3Writer().write(s, pom)
        return s.toString()
    }
}
