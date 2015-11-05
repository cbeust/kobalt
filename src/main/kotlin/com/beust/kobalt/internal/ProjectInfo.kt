package com.beust.kobalt.internal

import java.util.*

/**
 * Data that is useful for projects to have but should not be specified in the DSL.
 */
interface IProjectInfo {
    val defaultSourceDirectories: ArrayList<String>
    val defaultTestDirectories: ArrayList<String>
}
