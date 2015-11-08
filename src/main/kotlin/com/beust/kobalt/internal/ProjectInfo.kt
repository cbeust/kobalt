package com.beust.kobalt.internal

import java.util.*

/**
 * Data that is useful for projects to have but should not be specified in the DSL.
 */
interface IProjectInfo {
    val defaultSourceDirectories: HashSet<String>
    val defaultTestDirectories: HashSet<String>
}
