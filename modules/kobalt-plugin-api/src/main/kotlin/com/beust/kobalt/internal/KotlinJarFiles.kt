package com.beust.kobalt.internal

import com.beust.kobalt.maven.DependencyManager
import com.google.inject.Inject
import java.io.File

/**
 * The jar files that Kotlin needs to run.
 */
class KotlinJarFiles @Inject constructor(val dependencyManager: DependencyManager,
        val settings: KobaltSettings){
    private fun getKotlinCompilerJar(name: String): File {
        val id = "org.jetbrains.kotlin:kotlin-$name:${settings.kobaltCompilerVersion}"
        val dep = dependencyManager.create(id)
        return dep.jarFile.get().absoluteFile
    }

    val stdlib: File get() = getKotlinCompilerJar("stdlib")
    val compiler: File get() = getKotlinCompilerJar("compiler-embeddable")
}
