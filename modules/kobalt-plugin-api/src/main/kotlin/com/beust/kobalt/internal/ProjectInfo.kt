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

    /**
     * Add all the fields found in 1) the field contributors 2) the build configs and 3) the default config
     */
    fun generateCommonPart(project: Project, context: KobaltContext, buildConfigs: List<BuildConfig>) : List<String> {
        val result = arrayListOf<String>()

        // Fields from the field contributors
        result.addAll(generateFieldsFromContributors(project, context))

        val seen = hashSetOf<BuildConfig.Field>()

        // Fields from the build config
        buildConfigs.forEach {
            it.fields.forEach { field ->
                result.add(generate(field.type, field.name, field.value))
                seen.add(field)
            }
        }

        // Add all the fields in the default config that haven't been added yet
        project.defaultConfig?.let {
            it.fields.filter { ! seen.contains(it) }.forEach {
                result.add(generate(it.type, it.name, it.value))
            }
        }
        return result
    }

    fun generate(type: String, name: String, value: Any) = generate(BuildConfigField(type, name, value))

    fun generateFieldsFromContributors(project: Project, context: KobaltContext)
        = context.pluginInfo.buildConfigFieldContributors.flatMap {
                it.fieldsFor(project, context)
            }.map {
                generate(it)
            }
}
