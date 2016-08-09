package com.beust.kobalt.plugin.application

import com.beust.kobalt.api.IJvmFlagContributor
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.JarUtils
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import com.google.inject.Inject
import java.io.File

/**
 * Used for projects that define native() dependencies. This class extracts the native jar files in the
 * $build/native library and configures -Djava.library.path with that directory.
 */
class NativeManager @Inject constructor() : IJvmFlagContributor {
    fun buildDir(project: Project) = File(KFiles.nativeBuildDir(project))

    override fun jvmFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>): List<String> {
        return if (project.nativeDependencies.any()) listOf("-Djava.library.path=${buildDir(project)}")
            else emptyList()
    }

    fun installLibraries(project: Project) {
        val buildDir = buildDir(project)
        if (! buildDir.exists()) {
            buildDir.mkdirs()
            project.nativeDependencies.forEach { dep ->
                kobaltLog(2, "Extracting $dep " + dep.jarFile.get() + " in $buildDir")
                JarUtils.extractJarFile(dep.jarFile.get(), buildDir)
            }
        } else {
            kobaltLog(2, "Native directory $buildDir already exists, not extracting the native jar files")
        }
    }
}
