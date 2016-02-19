package com.beust.kobalt

import com.beust.kobalt.api.*
import com.beust.kobalt.internal.ActorUtils
import com.beust.kobalt.internal.SourceSet
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.util.*

/**
 * Capture the product flavor and the build type of a build.
 */
class Variant(val initialProductFlavor: ProductFlavorConfig? = null,
        val initialBuildType: BuildTypeConfig? = null) {

    val productFlavor: ProductFlavorConfig by lazy {
        initialProductFlavor ?: Variant.DEFAULT_PRODUCT_FLAVOR
    }
    val buildType: BuildTypeConfig by lazy {
        initialBuildType ?: Variant.DEFAULT_BUILD_TYPE
    }

    val isDefault : Boolean
        get() = productFlavor == DEFAULT_PRODUCT_FLAVOR && buildType == DEFAULT_BUILD_TYPE

    fun toTask(taskName: String) = taskName + productFlavor.name.capitalize() + buildType.name.capitalize()

    /**
     * for {internal, release}, return [internalRelease, internal, release]
     */
    fun allDirectories(project: Project): List<String> {
        val result = arrayListOf<String>()
        result.add(toCamelcaseDir())
        if (productFlavor != null) result.add(productFlavor.name)
        if (buildType != null) result.add(buildType.name)
        return result
    }

    fun sourceDirectories(project: Project, context: KobaltContext, sourceSet: SourceSet) : List<File> {
        val result = arrayListOf<File>()
        val compilerContributors = ActorUtils.selectAffinityActors(project, context,
                context.pluginInfo.compilerContributors)
        compilerContributors.forEach {
            it.compilersFor(project, context).forEach { compiler ->
                result.addAll(sourceDirectories(project, compiler.sourceDirectory, variantFirst = true,
                        sourceSet = sourceSet))
            }

        }
        return result.toList()
    }

    /**
     * Might be used by plug-ins.
     */
    fun resourceDirectories(project: Project, sourceSet: SourceSet = SourceSet.MAIN)
            = sourceDirectories(project, "resources", variantFirst = false, sourceSet = sourceSet)
        .filter { it.path.contains("resources") || it.path.contains("res") }

    /**
     * suffix is either "java" (to find source files) or "resources" (to find resources).
     * The priority directory is always returned first. For example, if a "pro" product flavor
     * is requested, "src/pro/kotlin" will appear in the result before "src/main/kotlin". Later,
     * files that have already been seen get skipped, which is how compilation and resources
     * receive the correct priority in the final jar.
     */
    private fun sourceDirectories(project: Project, suffix: String, variantFirst: Boolean, sourceSet: SourceSet)
            : List<File> {
        val result = arrayListOf<File>()
        val sourceDirectories =
            if (sourceSet == SourceSet.MAIN) {
                project.sourceDirectories.map { File(it) }
            } else if (sourceSet == SourceSet.TEST){
                project.sourceDirectoriesTest.map { File(it) }
            } else {
                throw KobaltException("Unknown source set: $sourceSet")
            }
        if (isDefault) {
            result.addAll(sourceDirectories)
        } else {
//            // The ordering of files is: 1) build type 2) product flavor 3) default
            buildType.let {
                val dir = File(KFiles.joinDir("src", it.name, suffix))
                log(3, "Adding source for build type ${it.name}: ${dir.path}")
                result.add(dir)
            }
            productFlavor.let {
                val dir = File(KFiles.joinDir("src", it.name, suffix))
                log(3, "Adding source for product flavor ${it.name}: ${dir.path}")
                result.add(dir)
            }

            result.addAll(allDirectories(project).map {
                File(KFiles.joinDir("src", it, suffix))
            }.filter {
                it.exists()
            })

            // Now that all the variant source directories have been added, add the project's default ones
            result.addAll(sourceDirectories)
        }

        // Generated directory, if applicable
        generatedSourceDirectory?.let {
            result.add(it)
        }

        val filteredResult = result.filter { File(project.directory, it.path).exists() }
        val sortedResult = if (variantFirst) filteredResult
            else filteredResult.reversed().toList()
        val deduplicatedResult = LinkedHashSet(sortedResult).toList()
        return deduplicatedResult
    }

    fun archiveName(project: Project, archiveName: String?, suffix: String) : String {
        val result =
            if (isDefault) {
                archiveName ?: project.name + "-" + project.version + suffix
            } else {
                val base = if (archiveName != null) archiveName.substring(0, archiveName.length - suffix.length)
                    else project.name + "-" + project.version
                val flavor = if (productFlavor.name.isEmpty()) "" else "-" + productFlavor.name
                val type = if (buildType.name.isEmpty()) "" else "-" + buildType.name
                val result: String = base + flavor + type + suffix

                result
            }
        return result
    }

    val shortArchiveName = if (isDefault) "" else "-" + productFlavor.name + "-" + buildType.name

    var generatedSourceDirectory: File? = null

    private fun findBuildTypeBuildConfig(project: Project, variant: Variant?) : BuildConfig? {
        val buildTypeName = variant?.buildType?.name
        return project.buildTypes[buildTypeName]?.buildConfig ?: null
    }

    private fun findProductFlavorBuildConfig(project: Project, variant: Variant?) : BuildConfig? {
        val buildTypeName = variant?.productFlavor?.name
        return project.productFlavors[buildTypeName]?.buildConfig ?: null
    }

    /**
     * Return a list of the BuildConfigs found on the productFlavor{}, buildType{} and project{} (in that order).
     */
    private fun findBuildConfigs(project: Project, variant: Variant?) : List<BuildConfig> {
        val result = listOf(
                findBuildTypeBuildConfig(project, variant),
                findProductFlavorBuildConfig(project, variant),
                project.buildConfig)
            .filterNotNull()

        return result
    }

    /**
     * Generate BuildConfig.java if requested. Also look up if any BuildConfig is defined on the current build type,
     * product flavor or main project, and use them to generate any additional field (in that order to
     * respect the priorities). Return the generated file if it was generated, null otherwise.
     */
    fun maybeGenerateBuildConfig(project: Project, context: KobaltContext) : File? {
        val buildConfigs = findBuildConfigs(project, this)

        if (buildConfigs.size > 0) {
            val pkg = project.packageName ?: project.group
                    ?: throw KobaltException(
                    "packageName needs to be defined on the project in order to generate BuildConfig")

            val contributor = ActorUtils.selectAffinityActor(context.pluginInfo.buildConfigContributors, project)
            if (contributor != null) {
                val code = contributor.generateBuildConfig(project, context, pkg, this, buildConfigs)
                val result = KFiles.makeDir(KFiles.generatedSourceDir(project, this, "buildConfig"))
                // Make sure the generatedSourceDirectory doesn't contain the project.directory since
                // that directory will be added when trying to find recursively all the sources in it
                generatedSourceDirectory = File(result.relativeTo(File(project.directory)).absolutePath)
                val outputGeneratedSourceDirectory = File(result, pkg.replace('.', File.separatorChar))
                val outputDir = File(outputGeneratedSourceDirectory, "BuildConfig." + contributor.buildConfigSuffix)
                KFiles.saveFile(outputDir, code)
                log(2, "Generated ${outputDir.path}")
                return result
            } else {
                throw KobaltException("Couldn't find a contributor to generate BuildConfig")
            }
        } else {
            return null
        }
    }

    override fun toString() = toTask("")

    companion object {
        val DEFAULT_PRODUCT_FLAVOR = ProductFlavorConfig("")
        val DEFAULT_BUILD_TYPE = BuildTypeConfig(null, "")

        fun allVariants(project: Project): List<Variant> {
            val result = arrayListOf<Variant>()

            if (project.buildTypes.size > 0) {
                project.buildTypes.keys.forEach {
                    val bt = project.buildTypes[it]
                    if (project.productFlavors.size > 0) {
                        project.productFlavors.keys.forEach {
                            result.add(Variant(project.productFlavors[it], bt))
                        }
                    } else {
                        result.add(Variant(null, bt))
                    }
                }
            } else if (project.productFlavors.size > 0) {
                project.productFlavors.keys.forEach {
                    val pf = project.productFlavors[it]
                    if (project.buildTypes.size > 0) {
                        project.buildTypes.keys.forEach {
                            result.add(Variant(pf, project.buildTypes[it]))
                        }
                    } else {
                        result.add(Variant(pf, null))
                    }
                }
            }
            return result
        }
    }

    fun toCamelcaseDir() : String {
        fun lci(s : String) = if (s.length == 0 || s.length == 1) s else s[0].toLowerCase() + s.substring(1)

        return lci(productFlavor.name) + buildType.name.capitalize()
    }

    fun toIntermediateDir() : String {
        if (isDefault) {
            return ""
        } else {
            return KFiles.joinDir(productFlavor.name, buildType.name)
        }
    }
}