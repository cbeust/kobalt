package com.beust.kobalt

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.archive.Archives
import com.beust.kobalt.archive.Jar
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.misc.*
import com.google.inject.Inject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.file.Paths
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class JarGenerator @Inject constructor(val dependencyManager: DependencyManager) {
    companion object {
        fun findIncludedFiles(directory: String, files: List<IncludedFile>, excludes: List<Glob>)
                : List<IncludedFile> {
            val result = arrayListOf<IncludedFile>()
            files.forEach { includedFile ->
                val includedSpecs = arrayListOf<IFileSpec>()
                includedFile.specs.forEach { spec ->
                    val fromPath = includedFile.from
                    if (File(directory, fromPath).exists()) {
                        spec.toFiles(directory, fromPath).forEach { file ->
                            val fullFile = File(KFiles.joinDir(directory, fromPath, file.path))
                            if (! fullFile.exists()) {
                                throw AssertionError("File should exist: $fullFile")
                            }

                            if (!KFiles.isExcluded(fullFile, excludes)) {
                                val normalized = Paths.get(file.path).normalize().toFile().path
                                includedSpecs.add(IFileSpec.FileSpec(normalized))
                            } else {
                                log(2, "Not adding ${file.path} to jar file because it's excluded")
                            }

                        }
                    } else {
                        log(2, "Directory $fromPath doesn't exist, not including it in the jar")
                    }
                }
                if (includedSpecs.size > 0) {
                    log(3, "Including specs $includedSpecs")
                    result.add(IncludedFile(From(includedFile.from), To(includedFile.to), includedSpecs))
                }
            }
            return result
        }
    }

    fun findIncludedFiles(project: Project, context: KobaltContext, jar: Jar) : List<IncludedFile> {
        //
        // Add all the applicable files for the current project
        //
        val buildDir = KFiles.buildDir(project)
        val result = arrayListOf<IncludedFile>()
        val classesDir = KFiles.makeDir(buildDir.path, "classes")

        if (jar.includedFiles.isEmpty()) {
            // If no includes were specified, assume the user wants a simple jar file made of the
            // classes of the project, so we specify a From("build/classes/"), To("") and
            // a list of files containing everything under it
            val relClassesDir = Paths.get(project.directory).relativize(Paths.get(classesDir.path))
            val prefixPath = Paths.get(project.directory).relativize(Paths.get(classesDir.path + "/"))

            // Class files
            val files = KFiles.findRecursively(classesDir).map { File(relClassesDir.toFile(), it) }
            val filesNotExcluded : List<File> = files.filter {
                ! KFiles.Companion.isExcluded(KFiles.joinDir(project.directory, it.path), jar.excludes)
            }
            val fileSpecs = arrayListOf<IFileSpec>()
            filesNotExcluded.forEach {
                fileSpecs.add(IFileSpec.FileSpec(it.path.toString().substring(prefixPath.toString().length + 1)))
            }
            result.add(IncludedFile(From(prefixPath.toString()), To(""), fileSpecs))

            // Resources, if applicable
            context.variant.resourceDirectories(project).forEach {
                result.add(IncludedFile(From(it.path), To(""), listOf(IFileSpec.GlobSpec("**"))))
            }
        } else {
            //
            // The user specified an include, just use it verbatim
            //
            val includedFiles = findIncludedFiles(project.directory, jar.includedFiles, jar.excludes)
            result.addAll(includedFiles)
        }

        //
        // If fatJar is true, add all the transitive dependencies as well: compile, runtime and dependent projects
        //
        if (jar.fatJar) {
            log(2, "Finding included files for fat jar")

            val seen = hashSetOf<String>()
            @Suppress("UNCHECKED_CAST")
            val allDependencies = project.compileDependencies + project.compileRuntimeDependencies +
                context.variant.buildType.compileDependencies +
                context.variant.buildType.compileRuntimeDependencies +
                context.variant.productFlavor.compileDependencies +
                context.variant.productFlavor.compileRuntimeDependencies
            val transitiveDependencies = dependencyManager.calculateDependencies(project, context,
                    listOf(Scope.COMPILE), allDependencies)
            transitiveDependencies.map {
                it.jarFile.get()
            }.forEach { file : File ->
                if (! seen.contains(file.path)) {
                    seen.add(file.path)
                    if (! KFiles.Companion.isExcluded(file, jar.excludes)) {
                        result.add(IncludedFile(specs = arrayListOf(IFileSpec.FileSpec(file.absolutePath)),
                                expandJarFiles = true))
                    }
                }
            }
        }

        return result
    }

    fun generateJar(project: Project, context: KobaltContext, jar: Jar) : File {
        val includedFiles = findIncludedFiles(project, context, jar)

        //
        // Generate the manifest
        // If manifest attributes were specified in the build file, use those to generateAndSave the manifest. Otherwise,
        // try to find a META-INF/MANIFEST.MF and use that one if we find any. Otherwise, use the default manifest.
        //
        val manifest =
            if (jar.attributes.size > 1) {
                log(2, "Creating MANIFEST.MF from " + jar.attributes.size + " attributes")
                Manifest().apply {
                    jar.attributes.forEach { attribute ->
                        mainAttributes.putValue(attribute.first, attribute.second)
                    }
                }
            } else {
                fun findManifestFile(project: Project, includedFiles: List<IncludedFile>): File? {
                    val allFiles = includedFiles.flatMap { file ->
                        file.allFromFiles(project.directory).map { file.from(it.path) }
                    }
                    val manifestFiles = allFiles.filter { it.path.contains("META-INF/MANIFEST.MF") }
                    return if (manifestFiles.any()) manifestFiles[0] else null
                }

                val manifestFile = findManifestFile(project, includedFiles)
                if (manifestFile != null) {
                    log(2, "Including MANIFEST.MF file $manifestFile")
                    Manifest(FileInputStream(manifestFile))
                } else {
                    Manifest()
                }
            }

        val jarFactory = { os: OutputStream -> JarOutputStream(os, manifest) }

        return Archives.generateArchive(project, context, jar.name, ".jar", includedFiles,
                true /* expandJarFiles */, jarFactory)
    }

}