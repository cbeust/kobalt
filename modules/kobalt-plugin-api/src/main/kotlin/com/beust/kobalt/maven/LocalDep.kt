package com.beust.kobalt.maven

open public class LocalDep(override val mavenId: MavenId, open val localRepo: LocalRepo)
        : SimpleDep(mavenId) {

    fun toAbsoluteJarFilePath(v: String) = localRepo.toFullPath(toJarFile(v))

    fun toAbsolutePomFile(v: String) = localRepo.toFullPath(listOf(toPomFile(v)).joinToString("/"))
}
