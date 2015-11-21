package com.beust.kobalt

import com.beust.kobalt.api.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File

/**
 * Capture the product flavor and the build type of a build.
 */
class Variant(val productFlavor: ProductFlavorConfig? = null, val buildType: BuildTypeConfig? = null) {
    val isDefault : Boolean
        get() = productFlavor == null && buildType == null

    fun toTask(taskName: String) = taskName +
            (productFlavor?.name?.capitalize() ?: "") +
            (buildType?.name?.capitalize() ?: "")

    fun variantSourceDirectories(context: KobaltContext) : List<File> {
        val result =
            if (isDefault) listOf("src/main")
            else (listOf(buildType?.name) + listOf(productFlavor?.name)).filterNotNull()
        return result.map { File(it) }
    }

    fun sourceDirectories(project: Project) : List<File> {
        val sourceDirectories = project.sourceDirectories.map { File(it) }
        if (isDefault) return sourceDirectories
        else {
            val result = arrayListOf<File>()
            // The ordering of files is: 1) build type 2) product flavor 3) default
            buildType?.let {
                val dir = File(KFiles.joinDir("src", it.name, project.projectInfo.sourceDirectory))
                log(2, "Adding source for build type ${it.name}: ${dir.path}")
                result.add(dir)
            }
            productFlavor?.let {
                val dir = File(KFiles.joinDir("src", it.name, project.projectInfo.sourceDirectory))
                log(2, "Adding source for product flavor ${it.name}: ${dir.path}")
                result.add(dir)
            }

            // Generated directory, if applicable
            generatedSourceDirectory?.let {
                result.add(it)
            }

            // Now that all the variant source directories have been added, add the project's default ones
            result.addAll(sourceDirectories)
            return result
        }
    }

    fun archiveName(project: Project, archiveName: String?, suffix: String) : String {
        val result: String =
            if (isDefault) archiveName ?: project.name + "-" + project.version + suffix
            else {
                val base = if (archiveName != null) archiveName.substring(0, archiveName.length - suffix.length)
                        else project.name + "-" + project.version
                base +
                    if (productFlavor == null) "" else "-${productFlavor.name}" +
                    if (buildType == null) "" else "-${buildType.name}" +
                    suffix

            }
        return result
    }

    val hasBuildConfig: Boolean
        get() {
            return productFlavor?.buildConfig != null || buildType?.buildConfig != null
        }

    var generatedSourceDirectory: File? = null

    /**
     * If either the Project or the current variant has a build config defined, generate BuildConfig.java
     */
    fun maybeGenerateBuildConfig(project: Project, context: KobaltContext) {
        fun generated(project: Project) = KFiles.joinDir(project.buildDirectory!!, "generated", "source")

        if (project.buildConfig != null || context.variant.hasBuildConfig) {
            val buildConfigs = arrayListOf<BuildConfig>()
            if (project.buildConfig != null) buildConfigs.add(project.buildConfig!!)
            with (context.variant) {
                if (buildType?.buildConfig != null) buildConfigs.add(buildType?.buildConfig!!)
                if (productFlavor?.buildConfig != null) buildConfigs.add(productFlavor?.buildConfig!!)
            }
            var pkg = project.packageName ?: project.group
                    ?: throw KobaltException(
                        "packageName needs to be defined on the project in order to generate BuildConfig")
            val code = project.projectInfo.generateBuildConfig(pkg, context.variant, buildConfigs)
            generatedSourceDirectory = KFiles.makeDir(generated(project), pkg.replace('.', File.separatorChar))
            val outputFile = File(generatedSourceDirectory, "BuildConfig" + project .sourceSuffix)
            KFiles.saveFile(outputFile, code)
            log(2, "Generated ${outputFile.path}")
        }
    }

    override fun toString() = toTask("")

    companion object {
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
}