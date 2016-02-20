package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles

enum class SourceSet {
    MAIN,
    TEST;

    fun correctSourceSet(project: Project) = when(this) {
        SourceSet.MAIN -> project.sourceDirectories
        SourceSet.TEST -> project.sourceDirectoriesTest
        else -> unknown(this)
    }

    fun correctOutputDir(project: Project) = when(this) {
        SourceSet.MAIN -> KFiles.CLASSES_DIR
        SourceSet.TEST -> KFiles.TEST_CLASSES_DIR
        else -> unknown(this)
    }

    companion object {
        fun of(isTest: Boolean) = if (isTest) TEST else MAIN
        private fun unknown(sourceSet: SourceSet) = throw KobaltException("Unknown source set: $sourceSet")
    }
}

