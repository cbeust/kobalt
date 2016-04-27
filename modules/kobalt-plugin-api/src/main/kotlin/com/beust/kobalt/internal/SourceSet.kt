package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles

enum class SourceSet(val outputDir: String) {
    MAIN(KFiles.CLASSES_DIR),
    TEST(KFiles.TEST_CLASSES_DIR);

    fun correctSourceSet(project: Project) = when(this) {
        SourceSet.MAIN -> project.sourceDirectories
        SourceSet.TEST -> project.sourceDirectoriesTest
        else -> unknown(this)
    }

    companion object {
        fun of(isTest: Boolean) = if (isTest) TEST else MAIN
        private fun unknown(sourceSet: SourceSet) : Nothing = throw KobaltException("Unknown source set: $sourceSet")
    }
}

