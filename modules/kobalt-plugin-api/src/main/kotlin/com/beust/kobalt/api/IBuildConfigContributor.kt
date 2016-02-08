package com.beust.kobalt.api

import com.beust.kobalt.Variant

/**
 * Plug-ins that can generate a BuildConfig file.
 */
interface IBuildConfigContributor : ISimpleAffinity<Project> {
    fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String, variant: Variant,
            buildConfigs: List<BuildConfig>) : String

    /**
     * The suffix of the generated BuildConfig, e.g. ".java".
     */
    val suffix: String
}
