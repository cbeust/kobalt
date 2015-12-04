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

        /**
         * Use the android home define on the project if any, otherwise use the environment variable.
         */
        fun androidHomeNoThrows(project: Project?, config: AndroidConfig?): String? {
            var result = System.getenv("ANDROID_HOME")
            if (project != null && config?.androidHome != null) {
                result = config?.androidHome
            }

            return result
        }

        fun androidHome(project: Project?, config: AndroidConfig) = androidHomeNoThrows(project, config) ?:
                throw IllegalArgumentException("Neither androidHome nor \$ANDROID_HOME were defined")

        fun generatedSourceDir(project: Project) = KFiles.joinDir(AndroidFiles.generated(project), "source")
    }
}
