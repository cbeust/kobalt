package com.beust.kobalt

import com.beust.kobalt.api.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.android.AndroidFiles
import com.beust.kobalt.plugin.android.AndroidPlugin
import java.io.File

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

    fun sourceDirectories(project: Project) : List<File> {
        val result = arrayListOf<File>()
        val sourceDirectories = project.sourceDirectories.map { File(it) }
        if (isDefault) {
            result.addAll(sourceDirectories)
        } else {
            // The ordering of files is: 1) build type 2) product flavor 3) default
            buildType.let {
                val dir = File(KFiles.joinDir("src", it.name, project.projectInfo.sourceDirectory))
                log(2, "Adding source for build type ${it.name}: ${dir.path}")
                result.add(dir)
            }
            productFlavor.let {
                val dir = File(KFiles.joinDir("src", it.name, project.projectInfo.sourceDirectory))
                log(2, "Adding source for product flavor ${it.name}: ${dir.path}")
                result.add(dir)
            }

            // Now that all the variant source directories have been added, add the project's default ones
            result.addAll(sourceDirectories)
            return result
        }

        // Generated directory, if applicable
        generatedSourceDirectory?.let {
            result.add(it)
        }

        return result
    }

    fun archiveName(project: Project, archiveName: String?, suffix: String) : String {
        val result =
            if (isDefault) {
                archiveName ?: project.name + "-" + project.version + suffix
            } else {
                val base = if (archiveName != null) archiveName.substring(0, archiveName.length - suffix.length)
                else project.name + "-" + project.version
                val result: String =
                        base + "-${productFlavor.name}" + "-${buildType.name}"

                result
            }
        return result
    }

    val shortArchiveName = if (isDefault) "" else "-" + productFlavor.name + "-" + buildType.name

    var generatedSourceDirectory: File? = null

    private fun findBuildTypeBuildConfig(project: Project, variant: Variant?) : BuildConfig? {
        val buildTypeName = variant?.buildType?.name
        return project.buildTypes.getRaw(buildTypeName)?.buildConfig ?: null
    }

    private fun findProductFlavorBuildConfig(project: Project, variant: Variant?) : BuildConfig? {
        val buildTypeName = variant?.productFlavor?.name
        return project.productFlavors.getRaw(buildTypeName)?.buildConfig ?: null
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
     * respect the priorities).
     */
    fun maybeGenerateBuildConfig(project: Project, context: KobaltContext) {
        fun generated(project: Project) = KFiles.joinDir(AndroidFiles.generated(project), "source")

        val buildConfigs = findBuildConfigs(project, context.variant)

        if (buildConfigs.size > 0) {
            val androidConfig = (Kobalt.findPlugin(AndroidPlugin.PLUGIN_NAME) as AndroidPlugin)
                    .configurationFor(project)
            val pkg = androidConfig?.applicationId ?: project.packageName ?: project.group
                    ?: throw KobaltException(
                    "packageName needs to be defined on the project in order to generate BuildConfig")

            val code = project.projectInfo.generateBuildConfig(pkg, context.variant, buildConfigs)
            val g = KFiles.makeDir(generated(project))
            // Make sure the generatedSourceDirectory doesn't contain the project.directory since
            // that directory will be added when trying to find recursively all the sources in it
            generatedSourceDirectory = File(g.relativeTo(File(project.directory)))
            val outputGeneratedSourceDirectory = File(g, pkg.replace('.', File.separatorChar))
            val outputFile = File(outputGeneratedSourceDirectory, "BuildConfig" + project.sourceSuffix)
            KFiles.saveFile(outputFile, code)
            log(2, "Generated ${outputFile.path}")
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
        val pfName = productFlavor.name
        val btName = buildType.name
        return pfName[0].toLowerCase() + pfName.substring(1) + btName[0].toUpperCase() + btName.substring(1)
    }

    fun toIntermediateDir() : String {
        if (isDefault) {
            return ""
        } else {
            return KFiles.joinDir(productFlavor.name, buildType.name)
        }
    }
}