package com.beust.kobalt.api

class BuildConfigField(val type: String, val name: String, val value: Any)

/**
 * Plug-ins that want to add fields to BuildConfig need to implement this interface.
 */
interface IBuildConfigFieldContributor {
    fun fieldsFor(project: Project, context: KobaltContext) : List<BuildConfigField>
}
