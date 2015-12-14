package com.beust.kobalt.maven

import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.inject.assistedinject.Assisted
import org.apache.maven.model.Developer
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import java.io.File
import java.io.StringWriter
import java.nio.charset.Charset
import javax.inject.Inject

public class PomGenerator @Inject constructor(@Assisted val project: Project) {
    interface IFactory {
        fun create(project: Project) : PomGenerator
    }

    fun generate() {
        requireNotNull(project.version, { "version mandatory on project ${project.name}" })
        requireNotNull(project.group, { "group mandatory on project ${project.name}" })
        requireNotNull(project.artifactId, { "artifactId mandatory on project ${project.name}" })

        val m = Model().apply {
            name = project.name
            artifactId = project.artifactId
            groupId = project.group
            version = project.version
            description = project.description
            licenses = project.licenses.map { it.toMavenLicense() }
            url = project.url
            scm = Scm().apply {
                project.scm?.let {
                    url = it.url
                    connection = it.connection
                    developerConnection = it.developerConnection
                }
            }
        }
        with(Developer()) {
            name = SystemProperties.username
            m.addDeveloper(this)
        }

        val dependencies = arrayListOf<org.apache.maven.model.Dependency>()
        m.dependencies = dependencies
        project.compileDependencies.forEach { dep ->
            dependencies.add(dep.toMavenDependencies())
        }

        val s = StringWriter()
        MavenXpp3Writer().write(s, m)

        val buildDir = KFiles.makeDir(project.directory, project.buildDirectory)
        val outputDir = KFiles.makeDir(buildDir.path, "libs")
        val mavenId = MavenId.create(project.group!!, project.artifactId!!, project.packaging, project.version!!)
        val pomFile = SimpleDep(mavenId).toPomFileName()
        val outputFile = File(outputDir, pomFile)
        outputFile.writeText(s.toString(), Charset.defaultCharset())
        log(1, "  Wrote $outputFile")
    }
}