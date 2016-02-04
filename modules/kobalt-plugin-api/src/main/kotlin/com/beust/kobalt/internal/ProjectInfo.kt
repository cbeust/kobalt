package com.beust.kobalt.internal

import com.beust.kobalt.Variant
import com.beust.kobalt.api.BuildConfig
import com.beust.kobalt.api.BuildConfigField
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project

/**
 * Data that is useful for projects to have but should not be specified in the DSL.
 */
interface IBuildConfig {
    /**
     * If at least one build config was found either on the project or the variant, this function
     * will be used to generate the BuildConfig file with the correct language.
     */
    fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String, variant: Variant,
            buildConfigs: List<BuildConfig>) : String

}

abstract class BaseBuildConfig : IBuildConfig {
    abstract fun generate(field: BuildConfigField) : String

    fun generate(type: String, name: String, value: Any) = generate(BuildConfigField(type, name, value))

    fun generateFieldsFromContributors(project: Project, context: KobaltContext)
        = context.pluginInfo.buildConfigFieldContributors.flatMap {
                it.fieldsFor(project, context)
            }.map {
                generate(it)
            }
}
