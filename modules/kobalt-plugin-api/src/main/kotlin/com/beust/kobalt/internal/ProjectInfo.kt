package com.beust.kobalt.internal

import com.beust.kobalt.Variant
import com.beust.kobalt.api.BuildConfig
import com.beust.kobalt.api.BuildConfigField
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import java.util.*

class LanguageInfo(val name: String, val suffix : String = name)

/**
 * Data that is useful for projects to have but should not be specified in the DSL.
 */
interface IProjectInfo {
    val languageInfos: List<LanguageInfo>

    val sourceSuffixes: List<String>

    val defaultSourceDirectories: HashSet<String>
    val defaultTestDirectories: HashSet<String>

    /**
     * If at least one build config was found either on the project or the variant, this function
     * will be used to generate the BuildConfig file with the correct language.
     */
    fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String, variant: Variant,
            buildConfigs: List<BuildConfig>) : String

    /**
     * The list of projects that this project depends on
     */
    val dependsOn: ArrayList<Project>

    var isDirty: Boolean

    /**
     * @return true if any of the projects we depend on is dirty.
     */
    fun dependsOnDirtyProjects(project: Project) = project.projectInfo.dependsOn.any { it.projectInfo.isDirty }
}

abstract class BaseProjectInfo(override val languageInfos: List<LanguageInfo>) : IProjectInfo {
    abstract fun generate(field: BuildConfigField) : String

    override val sourceSuffixes = languageInfos.map { it.suffix }

    fun generate(type: String, name: String, value: Any) = generate(BuildConfigField(type, name, value))

    fun generateFieldsFromContributors(project: Project, context: KobaltContext)
        = context.pluginInfo.buildConfigFieldContributors.flatMap {
                it.fieldsFor(project, context)
            }.map {
                generate(it)
            }

    override val dependsOn = arrayListOf<Project>()

    override var isDirty = false
}
