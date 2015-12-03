package com.beust.kobalt.plugin.android

import com.beust.kobalt.Variant
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles

class AndroidFiles {
    companion object {
        fun generated(project: Project) = KFiles.joinDir(project.directory, project.buildDirectory, "generated")

        fun intermediates(project: Project) = KFiles.joinDir(project.directory, project.buildDirectory,
                "intermediates")

        fun manifest(project: Project, context: KobaltContext) : String {
            return KFiles.joinDir(project.directory, "src/main", "AndroidManifest.xml")
        }

        fun mergedManifest(project: Project, variant: Variant) : String {
            val dir = KFiles.joinAndMakeDir(intermediates(project), "manifests", "full", variant.toIntermediateDir())
            return KFiles.joinDir(dir, "AndroidManifest.xml")
        }

        fun mergedResourcesNoVariant(project: Project) =
                KFiles.joinAndMakeDir(AndroidFiles.intermediates(project), "res", "merged")

        fun mergedResources(project: Project, variant: Variant) =
                KFiles.joinAndMakeDir(mergedResourcesNoVariant(project), variant.toIntermediateDir())
    }
}
