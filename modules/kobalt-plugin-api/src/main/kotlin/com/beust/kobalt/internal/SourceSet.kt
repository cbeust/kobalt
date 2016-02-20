package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles

enum class SourceSet {
    MAIN,
    TEST;

    fun correctSourceSet(project: Project) =
        if (this == SourceSet.MAIN) project.sourceDirectories
        else if (this == SourceSet.TEST) project.sourceDirectoriesTest
        else throw KobaltException("Unknown source set: $this")

    fun correctOutputDir(project: Project) =
        if (this == SourceSet.MAIN) KFiles.CLASSES_DIR
        else if (this == SourceSet.TEST) KFiles.TEST_CLASSES_DIR
        else throw IllegalArgumentException("Unknown source set: $this")

    companion object {
        fun of(isTest: Boolean) = if (isTest) TEST else MAIN

    }
}

