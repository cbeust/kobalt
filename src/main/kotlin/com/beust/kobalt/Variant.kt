package com.beust.kobalt

import com.beust.kobalt.api.BuildTypeConfig
import com.beust.kobalt.api.ProductFlavorConfig
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File

/**
 * Capture the product flavor and the build type of a build.
 */
class Variant(val productFlavor: ProductFlavorConfig? = null, val buildType: BuildTypeConfig? = null) {
    val isDefault : Boolean
        get() = productFlavor == null && buildType == null

    fun toTask(taskName: String) = taskName + productFlavor?.name?.capitalize() + buildType?.name?.capitalize()

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
}