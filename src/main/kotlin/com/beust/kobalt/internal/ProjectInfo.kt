package com.beust.kobalt.internal

import com.beust.kobalt.Variant
import com.beust.kobalt.api.BuildConfig
import java.util.*

/**
 * Data that is useful for projects to have but should not be specified in the DSL.
 */
interface IProjectInfo {
    /** Used to determine the last directory segment of the flavored sources, e.g. src/main/JAVA */
    val sourceDirectory : String

    val defaultSourceDirectories: HashSet<String>
    val defaultTestDirectories: HashSet<String>

    /**
     * If at least one build config was found either on the project or the variant, this function
     * will be used to generate the BuildConfig file with the correct language.
     */
    fun generateBuildConfig(packageName: String, variant: Variant, buildConfigs: List<BuildConfig>) : String
}
