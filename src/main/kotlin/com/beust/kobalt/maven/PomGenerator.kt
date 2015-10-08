package com.beust.kobalt.maven

import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.SystemProperties
import com.google.common.base.Preconditions
import com.google.inject.assistedinject.Assisted
import org.apache.maven.model.Developer
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import java.io.File
import java.io.StringWriter
import java.nio.charset.Charset
import javax.inject.Inject

public class PomGenerator @Inject constructor(@Assisted val project: Project) : KobaltLogger {
    interface IFactory {
        fun create(project: Project) : PomGenerator
    }

    fun generate() {
        Preconditions.checkNotNull(project.version, "version mandatory on project ${project.name}")
        Preconditions.checkNotNull(project.artifactId, "artifactId mandatory on project ${project.name}")
        val m = Model().apply {
            name = project.name
            artifactId = project.artifactId
            groupId = project.group
            version = project.version
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

        val buildDir = com.beust.kobalt.misc.KFiles.makeDir(project.directory, project.buildDirectory!!)
        val outputDir = com.beust.kobalt.misc.KFiles.makeDir(buildDir.path, "libs")
        val pomFile = SimpleDep(project.group!!, project.artifactId!!, project.version!!).toPomFileName()
        val outputFile = File(outputDir, pomFile)
        outputFile.writeText(s.toString(), Charset.defaultCharset())
        log(1, "Wrote ${outputFile}")
    }
}