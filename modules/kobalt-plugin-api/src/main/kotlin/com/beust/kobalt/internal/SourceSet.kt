package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles

enum class SourceSet {
    MAIN,
    TEST;

    private fun unknown(sourceSet: SourceSet) = throw KobaltException("Unknown source set: $this")

    fun correctSourceSet(project: Project) =
        if (this == SourceSet.MAIN) project.sourceDirectories
        else if (this == SourceSet.TEST) project.sourceDirectoriesTest
        else unknown(this)

    fun correctOutputDir(project: Project) =
        if (this == SourceSet.MAIN) KFiles.CLASSES_DIR
        else if (this == SourceSet.TEST) KFiles.TEST_CLASSES_DIR
        else unknown(this)

    companion object {
        fun of(isTest: Boolean) = if (isTest) TEST else MAIN

    }
}

