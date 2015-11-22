package com.beust.kobalt.plugin.android

import com.beust.kobalt.Variant
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.File
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Merges manifests and resources.
 */
class Merger @Inject constructor() {
    fun merge(project: Project, context: KobaltContext) {
        mergeAndroidManifest(project, context.variant)
        mergeResources(project, context.variant)
    }

    /**
     * TODO: not implemented yet, just copying the manifest to where the merged manifest should be.
     */
    private fun mergeAndroidManifest(project: Project, variant: Variant) {
        val dest = AndroidFiles.mergedManifest(project, variant)
        log(1, "Manifest merging not implemented, copying it to $dest")
        KFiles.copy(Paths.get("app/src/main/AndroidManifest.xml"),
                Paths.get(dest),
                StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * TODO: not implemented yet, just copying the resources into the variant dir
     * Spec: http://developer.android.com/sdk/installing/studio-build.html
     */
    private fun mergeResources(project: Project, variant: Variant) {
        val dest = AndroidFiles.Companion.mergedResources(project, variant)
        log(1, "Resource merging not implemented, copying app/src/main/res to $dest")
        listOf("main", variant.productFlavor.name, variant.buildType.name).forEach {
            log(1, "  Copying app/src/$it/res into $dest")
            KFiles.copyRecursively(File("app/src/$it/res"), File(dest), deleteFirst = false)
        }
    }

}
