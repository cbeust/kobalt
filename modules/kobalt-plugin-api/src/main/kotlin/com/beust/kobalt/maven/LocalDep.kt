package com.beust.kobalt.maven

import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.misc.Strings
import java.io.File
import java.util.concurrent.Future
import kotlin.properties.Delegates

open public class LocalDep(override val mavenId: MavenId, open val localRepo: LocalRepo)
        : SimpleDep(mavenId) {

    fun toAbsoluteJarFilePath(v: String) = localRepo.toFullPath(toJarFile(v))

    fun toAbsolutePomFile(v: String): String {
        return localRepo.toFullPath(Strings.Companion.join("/", arrayListOf(toPomFile(v))))
    }

}
