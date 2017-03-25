package com.beust.kobalt.internal

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KFiles
import com.google.inject.Inject
import java.io.File
import java.nio.file.Paths
import java.util.*

/**
 * Central place to compile files, used by plug-ins and non plug-ins.
 */
class CompilerUtils @Inject constructor(val files: KFiles, val dependencyManager: DependencyManager) {

    class CompilerResult(val successResults: List<TaskResult>, val failedResult: TaskResult?)

    fun invokeCompiler(project: Project, context: KobaltContext, compiler: ICompilerDescription,
            sourceDirectories: List<File>, isTest: Boolean, buildDirectory: File): CompilerResult {
        val results = arrayListOf<TaskResult>()
        var failedResult: TaskResult? = null
        val contributedSourceDirs =
                if (isTest) {
                    context.testSourceDirectories(project)
                } else {
                    context.sourceDirectories(project)
                }
        val sourceFiles = KFiles.findSourceFiles(project.directory,
                contributedSourceDirs.map { it.path }, compiler.sourceSuffixes)
        if (sourceFiles.isNotEmpty()) {
            // TODO: createCompilerActionInfo recalculates the source files, only compute them
            // once and pass them
            val info = createCompilerActionInfo(project, context, compiler, isTest,
                    sourceDirectories, sourceSuffixes = compiler.sourceSuffixes, buildDirectory = buildDirectory)
            val thisResult = invokeCompiler(project, context, compiler, info)
            results.addAll(thisResult.successResults)
            if (failedResult == null) {
                failedResult = thisResult.failedResult
            }
        } else {
            context.logger.log(project.name, 2,
                "${compiler.name} compiler not running on ${project.name} since no source files were found")
        }

        return CompilerResult(results, failedResult)
    }

    fun invokeCompiler(project: Project, context: KobaltContext, compiler: ICompilerDescription, info: CompilerActionInfo)
            : CompilerResult {
        val results = arrayListOf<TaskResult>()
        var failedResult: TaskResult? = null
        val thisResult = compiler.compile(project, context, info)
        results.add(thisResult)
        if (!thisResult.success && failedResult == null) {
            failedResult = thisResult
        }
        return CompilerResult(results, failedResult)
    }

    /**
     * Create a CompilerActionInfo (all the information that a compiler needs to know) for the given parameters.
     * Runs all the contributors and interceptors relevant to that task.
     */
    fun createCompilerActionInfo(project: Project, context: KobaltContext, compiler: ICompilerDescription,
            isTest: Boolean, sourceDirectories: List<File>, sourceSuffixes: List<String>, buildDirectory: File)
            : CompilerActionInfo {
        copyResources(project, context, SourceSet.of(isTest))

        val fullClasspath = dependencyManager.calculateDependencies(project, context,
                scopes = listOf(Scope.COMPILE, Scope.TEST))

        File(project.directory, buildDirectory.path).mkdirs()

        // Remove all the excluded dependencies from the classpath
        var classpath = fullClasspath

        // The classpath needs to contain $buildDirectory/classes as well so that projects that contain
        // multiple languages can use classes compiled by the compiler run before them.
        fun containsClassFiles(dir: File) =
                KFiles.containsCertainFile(dir) {
                    it.isFile && it.name.endsWith("class")
                }

//        if (buildDirectory.exists()) {
        if (containsClassFiles(buildDirectory)) {
            classpath += FileDependency(buildDirectory.path)
        }

        val initialSourceDirectories = ArrayList<File>(sourceDirectories)
        // Source directories from the contributors
        val contributedSourceDirs =
            if (isTest) {
                context.pluginInfo.testSourceDirContributors.flatMap { it.testSourceDirectoriesFor(project, context) }
            } else {
                context.pluginInfo.sourceDirContributors.flatMap { it.sourceDirectoriesFor(project, context) }
            }

        initialSourceDirectories.addAll(contributedSourceDirs)

        // Transform them with the interceptors, if any
        val allSourceDirectories =
            if (isTest) {
                initialSourceDirectories
            } else {
                context.pluginInfo.sourceDirectoriesInterceptors.fold(initialSourceDirectories.toList(),
                        { sd, interceptor -> interceptor.intercept(project, context, sd) })
            }.filter {
                File(project.directory, it.path).exists()
            }.filter {
                ! KFiles.isResource(it.path)
            }.distinctBy {
                Paths.get(it.path)
            }

        // Now that we have all the source directories, find all the source files in them. Note that
        // depending on the compiler's ability, sourceFiles can actually contain a list of directories
        // instead of individual source files.
        val projectDirectory = File(project.directory)
        val sourceFiles =
            if (compiler.canCompileDirectories) {
                allSourceDirectories.map { File(projectDirectory, it.path).path }
            } else {
                files.findRecursively(projectDirectory, allSourceDirectories,
                        { file -> sourceSuffixes.any { file.endsWith(it) } })
                        .map { File(projectDirectory, it).path }
            }

        // Special treatment if we are compiling Kotlin files and the project also has a java source
        // directory. In this case, also pass that java source directory to the Kotlin compiler as is
        // so that it can parse its symbols
        // Note: this should actually be queried on the compiler object so that this method, which
        // is compiler agnostic, doesn't hardcode Kotlin specific stuff
        val extraSourceFiles = arrayListOf<String>()

        fun containsJavaFiles(dir: File) =
            KFiles.containsCertainFile(dir) {
                it.isFile && it.name.endsWith("java")
            }

        if (sourceSuffixes.any { it.contains("kt")}) {
            val directories = if (isTest) project.sourceDirectoriesTest else project.sourceDirectories
            directories.forEach {
                val javaDir = File(KFiles.joinDir(project.directory, it))
                if (javaDir.exists() && containsJavaFiles(javaDir) && ! KFiles.isResource(javaDir.path)) {
                    extraSourceFiles.add(javaDir.path)
                    // Add all the source directories contributed as potential Java directories too
                    // (except our own)
                    context.pluginInfo.sourceDirContributors.forEach {
                        val sd = it.sourceDirectoriesFor(project, context).map { it.path }
                            .filter { ! it.contains("kotlin") }
                        if (! sd.contains("kotlin")) {
                            extraSourceFiles.addAll(sd)
                        }
                    }
                }
            }
        }

        val distinctSources = (sourceFiles + extraSourceFiles).distinctBy { File(it).toURI().normalize().path }
        val allSources = distinctSources
                .map { File(it).path }
                .distinct()
                .filter { File(it).exists() }

        // Finally, alter the info with the compiler interceptors before returning it
        val initialActionInfo = CompilerActionInfo(projectDirectory.path, classpath, allSources,
                sourceSuffixes, buildDirectory, emptyList() /* the flags will be provided by flag contributors */,
                emptyList(), context.internalContext.forceRecompile)
        val result = context.pluginInfo.compilerInterceptors.fold(initialActionInfo, { ai, interceptor ->
            interceptor.intercept(project, context, ai)
        })

        //
        // friendPaths
        //
        val friendPaths = KFiles.joinDir(project.buildDirectory, KFiles.CLASSES_DIR)

        return result
    }

    /**
     * Copy the resources from a source directory to the build one
     */
    private fun copyResources(project: Project, context: KobaltContext, sourceSet: SourceSet) {
        val outputDir = sourceSet.outputDir

        val variantSourceDirs = context.variant.resourceDirectories(project, sourceSet)
        if (variantSourceDirs.isNotEmpty()) {
            context.logger.log(project.name, 2, "Copying $sourceSet resources")
            val absOutputDir = File(KFiles.joinDir(project.directory, project.buildDirectory, outputDir))
            variantSourceDirs
                .map { File(project.directory, it.path) }
                .filter(File::exists)
                .forEach {
                    context.logger.log(project.name, 2, "Copying from $it to $absOutputDir")
                        KFiles.copyRecursively(it, absOutputDir, deleteFirst = false)
                }
        } else {
            context.logger.log(project.name, 2, "No resources to copy for $sourceSet")
        }
    }

    fun sourceCompilerFlags(project: Project?, context: KobaltContext, info: CompilerActionInfo) : List<String> {
        val adapters = context.pluginInfo.compilerFlagContributors.map {
            val closure = { project: Project, context: KobaltContext, currentFlags: List<String>,
                    suffixesBeingCompiled: List<String>
                -> it.compilerFlagsFor(project, context, currentFlags, suffixesBeingCompiled) }
            FlagContributor(it.flagPriority, closure)
        }
        return compilerFlags(project, context, info, adapters)
    }

    fun compilerFlags(project: Project?, context: KobaltContext, info: CompilerActionInfo,
            adapters: List<FlagContributor>) : List<String> {
        val result = arrayListOf<String>()
        if (project != null) {
            adapters.sortedBy { it.flagPriority }
            adapters.forEach {
                result.addAll(it.flagsFor(project, context, result, info.suffixesBeingCompiled))
            }
        }
        return result
    }
}
