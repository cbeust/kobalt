package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.IFileSpec
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.ProjectDescription
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.From
import com.beust.kobalt.misc.IncludedFile
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.To
import com.google.inject.Inject
import java.io.File
import java.io.OutputStream
import java.nio.file.Paths
import java.util.jar.JarOutputStream

class WarGenerator @Inject constructor(val dependencyManager: DependencyManager){

    companion object {
        val WEB_INF = "WEB-INF"
        val CLASSES = "$WEB_INF/classes"
        val LIB = "$WEB_INF/lib"
    }

    fun findIncludedFiles(project: Project, context: KobaltContext, war: War) : List<IncludedFile> {
        //
        // src/main/web app and classes
        //
        val result = arrayListOf(
                IncludedFile(From("src/main/webapp"), To(""), listOf(IFileSpec.GlobSpec("**"))),
                IncludedFile(From("kobaltBuild/classes"), To(CLASSES), listOf(IFileSpec.GlobSpec("**")))
        )

        //
        // The transitive closure of libraries goes into WEB-INF/libs.
        // Copy them all in kobaltBuild/war/WEB-INF/libs and create one IncludedFile out of that directory
        //
        val dependentProjects = listOf(ProjectDescription(project, project.projectExtra.dependsOn))
        val allDependencies = dependencyManager.calculateDependencies(project, context, dependentProjects,
                project.compileDependencies)

        val outDir = project.buildDirectory + "/war"
        val fullDir = outDir + "/" + LIB
        File(fullDir).mkdirs()

        // Run through all the classpath contributors and add their contributions to the libs/ directory
        context.pluginInfo.classpathContributors.map {
            it.classpathEntriesFor(project, context)
        }.map { deps : Collection<IClasspathDependency> ->
            deps.forEach { dep ->
                val jar = dep.jarFile.get()
                KFiles.copy(Paths.get(jar.path), Paths.get(fullDir, jar.name))
            }
        }

        // Add the regular dependencies to the libs/ directory
        allDependencies.map { it.jarFile.get() }.forEach {
            KFiles.copy(Paths.get(it.absolutePath), Paths.get(fullDir, it.name))
        }

        //
        // The resources
        //
        context.variant.resourceDirectories(project).forEach {
            result.add(IncludedFile(From(it.path), To(""), listOf(IFileSpec.GlobSpec("**"))))
        }

        result.add(IncludedFile(From(fullDir), To(LIB), listOf(IFileSpec.GlobSpec("**"))))

        //
        // Finally, all the included/excluded files specified in the war{} directive
        //
        result.addAll(PackagingPlugin.findIncludedFiles(project.directory, war.includedFiles, war.excludes))

        return result
    }

    fun generateWar(project: Project, context: KobaltContext, war: War) : File {

        val manifest = java.util.jar.Manifest()//FileInputStream(mf))
        war.attributes.forEach { attribute ->
            manifest.mainAttributes.putValue(attribute.first, attribute.second)
        }

        val allFiles = findIncludedFiles(project, context, war)
        val jarFactory = { os: OutputStream -> JarOutputStream(os, manifest) }
        return PackagingPlugin.generateArchive(project, context, war.name, ".war", allFiles,
                false /* don't expand jar files */, jarFactory)
    }

}
