package com.beust.kobalt.internal

import java.util.*

/**
 * Data that is useful for projects to have but should not be specified in the DSL.
 */
interface IProjectInfo {
    /** Used to determine the last directory segment of the flavored sources, e.g. src/main/JAVA */
    val sourceDirectory : String
    val defaultSourceDirectories: HashSet<String>
    val defaultTestDirectories: HashSet<String>
}
